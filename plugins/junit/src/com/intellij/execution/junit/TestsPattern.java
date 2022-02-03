// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.execution.*;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.execution.junit2.info.NestedClassLocation;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerComposite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

public class TestsPattern extends TestPackage {
  public TestsPattern(JUnitConfiguration configuration, ExecutionEnvironment environment) {
    super(configuration, environment);
  }

  @Override
  protected TestClassFilter getClassFilter(JUnitConfiguration.Data data) throws CantRunException {
    return TestClassFilter.create(getSourceScope(), getConfiguration().getConfigurationModule().getModule(), data.getPatternPresentation());
  }

  @NotNull
  @Override
  protected String getPackageName(JUnitConfiguration.Data data) {
    return "";
  }

  @Override
  protected boolean filterOutputByDirectoryForJunit5(Set<Location<?>> classNames) {
    return super.filterOutputByDirectoryForJunit5(classNames) && classNames.isEmpty();
  }

  @Override
  protected boolean requiresSmartMode() {
    return true;
  }

  @Override
  protected void searchTests5(Module module, Set<Location<?>> classes) {
    searchTests(module, null, classes, true);
  }

  @Override
  protected void searchTests(Module module, TestClassFilter classFilter, Set<Location<?>> classes) {
    searchTests(module, classFilter, classes, false);
  }

  private void searchTests(Module module, TestClassFilter classFilter, Set<Location<?>> classes, boolean junit5) {
    JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    Project project = getConfiguration().getProject();
    for (String className : data.getPatterns()) {
      final PsiClass psiClass = ReadAction.compute(() -> getTestClass(project, className));
      if (psiClass != null) {
        if (ReadAction.compute(() -> JUnitUtil.isTestClass(psiClass))) {
          classes.add(findLocation(className, psiClass, PsiLocation.fromPsiElement(psiClass)));
        }
      }
      else if (junit5 && className.contains("$")) { //OuterClass$InnerInSuper
        String topLevelClassName = StringUtil.getPackageName(className, '$');
        String nestedClassName = StringUtil.getShortName(className, '$');
        PsiClass cl = ReadAction.compute(() -> getTestClass(project, topLevelClassName));
        if (cl != null && ReadAction.compute(() -> JUnitUtil.isJUnit5TestClass(cl, false))) {
          PsiClass innerClassByName =
            cl.findInnerClassByName(nestedClassName.contains(",") ? StringUtil.getPackageName(nestedClassName, ',') : nestedClassName, true);
          if (innerClassByName != null) {
            classes.add(findLocation(nestedClassName, innerClassByName, NestedClassLocation.elementInClass(innerClassByName, cl)));
          }
        }
      }
      else {
        classes.clear();
        if (!junit5) {//junit 5 process tests automatically
          LinkedHashSet<PsiClass> psiClasses = new LinkedHashSet<>();
          ConfigurationUtil.findAllTestClasses(classFilter, module, psiClasses);
          psiClasses.stream().map(PsiLocation::fromPsiElement).forEach(classes::add);
        }
        return;
      }
    }
  }

  private static Location<?> findLocation(String className, PsiClass psiClass, Location<? extends PsiClass> classLocation) {
    if (className.contains(",")) {
      String shortName = StringUtil.getShortName(className, ',');
      PsiMethod[] methods = psiClass.findMethodsByName(shortName, true);
      if (methods.length > 0) {
        return new MethodLocation(psiClass.getProject(), methods[0], classLocation);
      }
    }
    return classLocation;
  }

  @Override
  protected String getFilters(Set<Location<?>> foundClasses, String packageName) {
    return foundClasses.isEmpty() ? getConfiguration().getPersistentData().getPatternPresentation() : "";
  }

  private PsiClass getTestClass(Project project, String className) {
    SourceScope sourceScope = getSourceScope();
    GlobalSearchScope searchScope = sourceScope != null ? sourceScope.getGlobalSearchScope() : GlobalSearchScope.allScope(project);
    return JavaExecutionUtil.findMainClass(project, className.contains(",") ? StringUtil.getPackageName(className, ',') : className, searchScope);
  }

  @Override
  protected boolean configureByModule(Module module) {
    return module != null;
  }

  @Override
  public String suggestActionName() {
    return null;
  }

  @Nullable
  @Override
  public RefactoringElementListener getListener(PsiElement element) {
    final RefactoringElementListenerComposite composite = new RefactoringElementListenerComposite();
    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    final Set<String> patterns = data.getPatterns();
    for (final String pattern : patterns) {
      final PsiClass testClass = getTestClass(getConfiguration().getProject(), pattern.trim());
      if (testClass != null && testClass.equals(element)) {
        final RefactoringElementListener listeners =
          RefactoringListeners.getListeners(testClass, new RefactoringListeners.Accessor<>() {
            private String myOldName = testClass.getQualifiedName();

            @Override
            public void setName(String qualifiedName) {
              final Set<String> replaced = new LinkedHashSet<>();
              for (String currentPattern : patterns) {
                if (myOldName.equals(currentPattern)) {
                  replaced.add(qualifiedName);
                  myOldName = qualifiedName;
                }
                else {
                  replaced.add(currentPattern);
                }
              }
              patterns.clear();
              patterns.addAll(replaced);
            }

            @Override
            public PsiClass getPsiElement() {
              return testClass;
            }

            @Override
            public void setPsiElement(PsiClass psiElement) {
              if (psiElement == testClass) {
                setName(psiElement.getQualifiedName());
              }
            }
          });
        if (listeners != null) {
          composite.addListener(listeners);
        }
      }
    }
    return composite;
  }

  @Override
  public boolean isConfiguredByElement(JUnitConfiguration configuration,
                                       PsiClass testClass,
                                       PsiMethod testMethod,
                                       PsiPackage testPackage,
                                       PsiDirectory testDir) {
    return false;
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    JavaParametersUtil.checkAlternativeJRE(getConfiguration());
    ProgramParametersUtil.checkWorkingDirectoryExist(getConfiguration(), getConfiguration().getProject(),
                                                     getConfiguration().getConfigurationModule().getModule());
    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    final Set<String> patterns = data.getPatterns();
    if (patterns.isEmpty()) {
      throw new RuntimeConfigurationWarning(JUnitBundle.message("no.pattern.error.message"));
    }
    if (DumbService.getInstance(getConfiguration().getProject()).isDumb()) return;
    final GlobalSearchScope searchScope = GlobalSearchScope.allScope(getConfiguration().getProject());
    for (String pattern : patterns) {
      final String className = pattern.contains(",") ? StringUtil.getPackageName(pattern, ',') : pattern;
      final PsiClass psiClass = JavaExecutionUtil.findMainClass(getConfiguration().getProject(), className, searchScope);
      if (psiClass != null && !JUnitUtil.isTestClass(psiClass)) {
        throw new RuntimeConfigurationWarning(JUnitBundle.message("class.not.test.error.message", className));
      }
      if (psiClass == null && !pattern.contains("*")) {
        throw new RuntimeConfigurationWarning(JavaBundle.message("class.not.found.error.message", className));
      }
    }
  }
}