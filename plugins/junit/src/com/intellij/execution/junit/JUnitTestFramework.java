/*
 * User: anna
 * Date: 15-Aug-2007
 */
package com.intellij.execution.junit;

import com.intellij.codeInsight.TestFramework;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

public class JUnitTestFramework implements TestFramework {
   public boolean isTestKlass(final PsiClass psiClass) {
    return JUnitUtil.isTestClass(psiClass);
  }

  public PsiMethod findSetUpMethod(final PsiClass psiClass) throws IncorrectOperationException {
    final PsiManager manager = psiClass.getManager();
    final PsiElementFactory factory = manager.getElementFactory();
    final PsiMethod patternMethod = factory.createMethodFromText("protected void setUp() throws Exception {\nsuper.setUp();\n}", null);
    PsiMethod inClass = psiClass.findMethodBySignature(patternMethod, false);
    if (inClass == null) {
      return (PsiMethod)psiClass.add(patternMethod);
    }
    else if (inClass.getBody() == null) {
      return (PsiMethod)inClass.replace(patternMethod);
    }
    return inClass;
  }
}