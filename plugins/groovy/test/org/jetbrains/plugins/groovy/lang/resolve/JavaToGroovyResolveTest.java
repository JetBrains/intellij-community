/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author ven
 */
public class JavaToGroovyResolveTest extends GroovyResolveTestCase {

  protected String getTestDataPath() {
    return TestUtils.getTestDataPath() + "/resolve/javaToGroovy/";
  }

  public void testField1() throws Exception {
    PsiReference ref = configureByFile("field1/A.java");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrField);
  }

  public void testMethod1() throws Exception {
    PsiJavaReference ref = (PsiJavaReference) configureByFile("method1/A.java");
    JavaResolveResult resolveResult = ref.advancedResolve(false);
    assertTrue(resolveResult.getElement() instanceof GrMethod);
    assertTrue(resolveResult.isValidResult());
  }

}