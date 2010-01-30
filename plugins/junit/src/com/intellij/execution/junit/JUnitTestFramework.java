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

/*
 * User: anna
 * Date: 15-Aug-2007
 */
package com.intellij.execution.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.TestFramework;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;

public class JUnitTestFramework implements TestFramework {
   public boolean isTestKlass(final PsiClass psiClass) {
    return JUnitUtil.isTestClass(psiClass);
  }

  public PsiMethod findSetUpMethod(final PsiClass psiClass) throws IncorrectOperationException {
    final PsiManager manager = psiClass.getManager();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    if (JUnitUtil.isJUnit4TestClass(psiClass)) {
      final PsiMethod[] methods = psiClass.getMethods();
      for (PsiMethod method : methods) {
        if (AnnotationUtil.isAnnotated(method, "org.junit.Before", false)) return method;
      }
      final PsiMethod method =
        (PsiMethod)psiClass.add(factory.createMethodFromText("@org.junit.Before public void setUp() throws Exception {\n}", null));
      JavaCodeStyleManager.getInstance(manager.getProject()).shortenClassReferences(method);
      return method;
    }

    final PsiMethod patternMethod = factory.createMethodFromText("protected void setUp() throws Exception {\nsuper.setUp();\n}", null);

    final PsiClass baseClass = psiClass.getSuperClass();
    if (baseClass != null) {
      final PsiMethod baseMethod = baseClass.findMethodBySignature(patternMethod, false);
      if (baseMethod != null && baseMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
        PsiUtil.setModifierProperty(patternMethod, PsiModifier.PROTECTED, false);
        PsiUtil.setModifierProperty(patternMethod, PsiModifier.PUBLIC, true);
      }
    }

    PsiMethod inClass = psiClass.findMethodBySignature(patternMethod, false);
    if (inClass == null) {
      return (PsiMethod)psiClass.add(patternMethod);
    }
    else if (inClass.getBody() == null) {
      return (PsiMethod)inClass.replace(patternMethod);
    }
    return inClass;
  }

  public boolean isTestMethodOrConfig(PsiMethod psiMethod) {
    return JUnitUtil.isTestMethodOrConfig(psiMethod);
  }
}