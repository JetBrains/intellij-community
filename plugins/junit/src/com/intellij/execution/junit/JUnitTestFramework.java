/*
 * User: anna
 * Date: 15-Aug-2007
 */
package com.intellij.execution.junit;

import com.intellij.codeInsight.TestFramework;
import com.intellij.psi.PsiClass;

public class JUnitTestFramework implements TestFramework {
   public boolean isTestKlass(final PsiClass psiClass) {
    return JUnitUtil.isTestClass(psiClass);
  }
}