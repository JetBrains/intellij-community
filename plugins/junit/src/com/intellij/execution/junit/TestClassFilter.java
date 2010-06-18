/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.TestUtil;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.execution.configurations.ConfigurationUtil;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;

public class TestClassFilter implements TreeClassChooser.ClassFilterWithScope {
  private final PsiClass myBase;
  private final Project myProject;
  private final GlobalSearchScope myScope;

  private TestClassFilter(@NotNull PsiClass base, final GlobalSearchScope scope) {
    myBase = base;
    myProject = base.getProject();
    myScope = scope;
  }

  public PsiManager getPsiManager() { return PsiManager.getInstance(myProject); }

  public Project getProject() { return myProject; }

  public boolean isAccepted(final PsiClass aClass) {
    return ConfigurationUtil.PUBLIC_INSTANTIATABLE_CLASS.value(aClass) &&
           (aClass.isInheritor(myBase, true) || JUnitUtil.isTestClass(aClass))
           && !CompilerConfiguration.getInstance(getProject()).isExcludedFromCompilation(PsiUtilBase.getVirtualFile(aClass))
      ;
  }

  public TestClassFilter intersectionWith(final GlobalSearchScope scope) {
    return new TestClassFilter(myBase, myScope.intersectWith(scope));
  }

  public static TestClassFilter create(final SourceScope sourceScope, Module module) throws JUnitUtil.NoJUnitException {
    if (sourceScope == null) throw new JUnitUtil.NoJUnitException();
    PsiClass testCase = module == null ? JUnitUtil.getTestCaseClass(sourceScope) : JUnitUtil.getTestCaseClass(module);
    return new TestClassFilter(testCase, sourceScope.getGlobalSearchScope());
  }

  public GlobalSearchScope getScope() { return myScope; }
  public PsiClass getBase() { return myBase; }
}
