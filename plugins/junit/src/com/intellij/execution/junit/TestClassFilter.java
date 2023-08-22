// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.execution.configurations.ConfigurationUtil;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.ide.util.ClassFilter;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiClassUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.indexing.DumbModeAccessType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_HIERARCHY;

public class TestClassFilter implements ClassFilter.ClassFilterWithScope {
  private final @Nullable PsiClass myBase;
  private final Project myProject;
  private final GlobalSearchScope myScope;

  private TestClassFilter(@Nullable PsiClass base, final GlobalSearchScope scope) {
    myBase = base;
    myProject = scope.getProject();
    myScope = scope;
  }

  public PsiManager getPsiManager() { return PsiManager.getInstance(myProject); }

  public Project getProject() { return myProject; }

  @Override
  public boolean isAccepted(final PsiClass aClass) {
    return ReadAction.compute(() -> {
      return DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
        if (aClass.getQualifiedName() != null &&
            (myBase != null && aClass.isInheritor(myBase, true) && ConfigurationUtil.PUBLIC_INSTANTIATABLE_CLASS.value(aClass) ||
             isTopMostTestClass(aClass))) {
          final CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(getProject());
          final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(aClass);
          if (virtualFile == null) return false;
          return !compilerConfiguration.isExcludedFromCompilation(virtualFile) &&
                 !ProjectRootManager.getInstance(myProject).getFileIndex()
                   .isUnderSourceRootOfType(virtualFile, JavaModuleSourceRootTypes.RESOURCES);
        }
        return false;
      });
    });
  }

  private static boolean isTopMostTestClass(PsiClass psiClass) {
    if (psiClass.getQualifiedName() == null) return false;

    if (!PsiClassUtil.isRunnableClass(psiClass, true, true)) return false;

    if (AnnotationUtil.isAnnotated(psiClass, JUnitUtil.RUN_WITH, CHECK_HIERARCHY)) return true;

    if (JUnitUtil.isTestCaseInheritor(psiClass)) return true;

    for (final PsiMethod method : psiClass.getAllMethods()) {
      if (JUnitUtil.isSuiteMethod(method)) return true;
      if (JUnitUtil.isTestAnnotated(method)) return true;
    }

    return false;
  }

  @NotNull
  public TestClassFilter intersectionWith(final GlobalSearchScope scope) {
    return new TestClassFilter(myBase, myScope.intersectWith(scope));
  }

  @NotNull
  public static TestClassFilter create(final SourceScope sourceScope, final Module module) throws JUnitUtil.NoJUnitException {
    final PsiClass testCase = getTestCase(sourceScope, module);
    return new TestClassFilter(testCase, sourceScope.getGlobalSearchScope());
  }

  @NotNull
  private static PsiClass getTestCase(final SourceScope sourceScope, final Module module) throws JUnitUtil.NoJUnitException {
    if (sourceScope == null) throw new JUnitUtil.NoJUnitException();
    return ReadAction.compute(() -> module == null ? JUnitUtil.getTestCaseClass(sourceScope) : JUnitUtil.getTestCaseClass(module));
  }

  public static TestClassFilter create(final SourceScope sourceScope, Module module, final String pattern) throws JUnitUtil.NoJUnitException {
    final PsiClass testCase = getTestCase(sourceScope, module);
    Predicate<String> predicate = getClassNamePredicate(pattern);
    return new TestClassFilter(testCase, sourceScope.getGlobalSearchScope()){
      @Override
      public boolean isAccepted(final PsiClass aClass) {
        if (super.isAccepted(aClass)) {
          final String qualifiedName = ReadAction.compute(() -> aClass.getQualifiedName());
          return predicate.test(qualifiedName);
        }
        return false;
      }
    };
  }

  private static Pattern getCompilePattern(String pattern) {
    Pattern compilePattern;
    try {
      compilePattern = Pattern.compile(pattern.trim());
    }
    catch (PatternSyntaxException e) {
      compilePattern = null;
    }
    return compilePattern;
  }

  public static Predicate<String> getClassNamePredicate(String pattern) {
    final String[] patterns = pattern.split("\\|\\|");
    final List<Pattern> compilePatterns = new ArrayList<>();
    for (String p : patterns) {
      final Pattern compilePattern = getCompilePattern(p);
      if (compilePattern != null) {
        compilePatterns.add(compilePattern);
      }
    }
    return qualifiedName -> {
      for (Pattern compilePattern : compilePatterns) {
        if (compilePattern.matcher(qualifiedName).matches()) {
          return true;
        }
      }
      return false;
    };
  }

  @Override
  public GlobalSearchScope getScope() { return myScope; }
  @Nullable
  public PsiClass getBase() { return myBase; }
}
