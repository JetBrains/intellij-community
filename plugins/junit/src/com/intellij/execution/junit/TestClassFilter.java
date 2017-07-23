/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.execution.junit;

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
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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

  public boolean isAccepted(final PsiClass aClass) {
    return ReadAction.compute(() -> {
      if (aClass.getQualifiedName() != null &&
          (myBase != null && aClass.isInheritor(myBase, true) && ConfigurationUtil.PUBLIC_INSTANTIATABLE_CLASS.value(aClass) ||
           JUnitUtil.isTestClass(aClass))) {
        final CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(getProject());
        final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(aClass);
        if (virtualFile == null) return false;
        return !compilerConfiguration.isExcludedFromCompilation(virtualFile) &&
               !ProjectRootManager.getInstance(myProject).getFileIndex()
                 .isUnderSourceRootOfType(virtualFile, JavaModuleSourceRootTypes.RESOURCES);
      }
      return false;
    });
  }

  public TestClassFilter intersectionWith(final GlobalSearchScope scope) {
    return new TestClassFilter(myBase, myScope.intersectWith(scope));
  }

  public static TestClassFilter create(final SourceScope sourceScope, final Module module) throws JUnitUtil.NoJUnitException {
    final PsiClass testCase = getTestCase(sourceScope, module);
    return new TestClassFilter(testCase, sourceScope.getGlobalSearchScope());
  }

  private static PsiClass getTestCase(final SourceScope sourceScope, final Module module) throws JUnitUtil.NoJUnitException {
    if (sourceScope == null) throw new JUnitUtil.NoJUnitException();
    final JUnitUtil.NoJUnitException[] ex = new JUnitUtil.NoJUnitException[1];
    final PsiClass testCase = ReadAction.compute(() -> {
      if (TestObject.isJUnit5(module, sourceScope, sourceScope.getProject())) {
        return null;
      }
      try {
        return module == null ? JUnitUtil.getTestCaseClass(sourceScope) : JUnitUtil.getTestCaseClass(module);
      }
      catch (JUnitUtil.NoJUnitException e) {
        ex[0] = e;
        return null;
      }
    });
    if (ex[0] != null) throw ex[0];
    return testCase;
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

  public GlobalSearchScope getScope() { return myScope; }
  @Nullable
  public PsiClass getBase() { return myBase; }
}
