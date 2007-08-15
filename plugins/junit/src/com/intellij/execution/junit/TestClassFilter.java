package com.intellij.execution.junit;

import com.intellij.codeInsight.TestUtil;
import com.intellij.execution.configurations.ConfigurationUtil;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;

public class TestClassFilter implements TreeClassChooser.ClassFilterWithScope {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit.TestClassFilter");
  private final PsiClass myBase;
  private final Project myProject;
  private final GlobalSearchScope myScope;

  private TestClassFilter(final PsiClass base, final GlobalSearchScope scope) {
    LOG.assertTrue(base != null);
    myBase = base;
    myProject = base.getProject();
    myScope = scope;
  }

  public PsiManager getPsiManager() { return PsiManager.getInstance(myProject); }

  public Project getProject() { return myProject; }

  public boolean isAccepted(final PsiClass aClass) {
    return ConfigurationUtil.PUBLIC_INSTANTIATABLE_CLASS.value(aClass) &&
           (aClass.isInheritor(myBase, true) || TestUtil.isTestClass(aClass));
  }

  public TestClassFilter intersectionWith(final GlobalSearchScope scope) {
    return new TestClassFilter(myBase, myScope.intersectWith(scope));
  }

  public static TestClassFilter create(final SourceScope sourceScope, Module module) throws JUnitUtil.NoJUnitException {
    if (sourceScope == null) throw new JUnitUtil.NoJUnitException();
    return new TestClassFilter(module != null ? JUnitUtil.getTestCaseClass(module) : JUnitUtil.getTestCaseClass(sourceScope),
                               sourceScope.getGlobalSearchScope());
  }

  public GlobalSearchScope getScope() { return myScope; }
  public PsiClass getBase() { return myBase; }
}
