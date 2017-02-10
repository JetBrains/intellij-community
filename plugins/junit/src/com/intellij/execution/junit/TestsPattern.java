/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * User: anna
 * Date: 11-Jun-2010
 */
package com.intellij.execution.junit;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.SearchForTestsTask;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerComposite;
import com.intellij.util.Function;
import com.intellij.util.FunctionUtil;
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

  @Override
  protected String getPackageName(JUnitConfiguration.Data data) throws CantRunException {
    return "";
  }

  @Override
  public SearchForTestsTask createSearchingForTestsTask() {
    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    final Project project = getConfiguration().getProject();
    final Set<String> classNames = new LinkedHashSet<>();
    for (String className : data.getPatterns()) {
      final PsiClass psiClass = getTestClass(project, className);
      if (psiClass != null&& JUnitUtil.isTestClass(psiClass)) {
        classNames.add(className);
      }
    }

    if (classNames.size() == data.getPatterns().size()) {
      return new SearchForTestsTask(project, myServerSocket) {
        @Override
        protected void search() throws ExecutionException {
          final Function<String, String> nameFunction = StringUtil.isEmpty(data.METHOD_NAME)
                                                        ? FunctionUtil.<String>id()
                                                        : (Function<String, String>)className -> className;
          addClassesListToJavaParameters(classNames, nameFunction, "", false, getJavaParameters());
        }

        @Override
        protected void onFound() {}
      };
    }

    return super.createSearchingForTestsTask();
  }

  private static PsiClass getTestClass(Project project, String className) {
    return JavaExecutionUtil.findMainClass(project,
                                           (className.contains(",")
                                           ? className.substring(0, className.indexOf(','))
                                           : className).trim(), GlobalSearchScope.allScope(project));
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
  public RefactoringElementListener getListener(PsiElement element, JUnitConfiguration configuration) {
    final RefactoringElementListenerComposite composite = new RefactoringElementListenerComposite();
    final JUnitConfiguration.Data data = configuration.getPersistentData();
    final Set<String> patterns = data.getPatterns();
    for (final String pattern : patterns) {
      final PsiClass testClass = getTestClass(configuration.getProject(), pattern.trim());
      if (testClass != null && testClass.equals(element)) {
        final RefactoringElementListener listeners =
          RefactoringListeners.getListeners(testClass, new RefactoringListeners.Accessor<PsiClass>() {
            private String myOldName = testClass.getQualifiedName();
            @Override
            public void setName(String qualifiedName) {
              final Set<String> replaced = new LinkedHashSet<>();
              for (String currentPattern : patterns) {
                if (myOldName.equals(currentPattern)) {
                  replaced.add(qualifiedName);
                  myOldName = qualifiedName;
                } else {
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
    /*if (testMethod != null && Comparing.strEqual(testMethod.getName(), configuration.getPersistentData().METHOD_NAME)) {
      return true;
    }*/
    return false;
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    final Set<String> patterns = data.getPatterns();
    if (patterns.isEmpty()) {
      throw new RuntimeConfigurationWarning("No pattern selected");
    }
    final GlobalSearchScope searchScope = GlobalSearchScope.allScope(getConfiguration().getProject());
    for (String pattern : patterns) {
      final String className = pattern.contains(",") ? StringUtil.getPackageName(pattern, ',') : pattern;
      final PsiClass psiClass = JavaExecutionUtil.findMainClass(getConfiguration().getProject(), className, searchScope);
      if (psiClass != null && !JUnitUtil.isTestClass(psiClass)) {
        throw new RuntimeConfigurationWarning("Class " + className + " not a test");
      }
    }
  }
}
