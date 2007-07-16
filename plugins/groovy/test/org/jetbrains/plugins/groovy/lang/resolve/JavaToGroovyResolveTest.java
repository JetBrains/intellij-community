/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptMethod;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author ven
 */
public class JavaToGroovyResolveTest extends GroovyResolveTestCase {

  protected String getTestDataPath() {
    return TestUtils.getTestDataPath() + "/resolve/javaToGroovy/";
  }

  public void testField1() throws Exception {
    PsiReference ref = configureByFile("field1/ABCF.java");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrField);
  }

  public void testMethod1() throws Exception {
    PsiJavaReference ref = (PsiJavaReference) configureByFile("method1/ABCF.java");
    JavaResolveResult resolveResult = ref.advancedResolve(false);
    assertTrue(resolveResult.getElement() instanceof GrMethod);
    assertTrue(resolveResult.isValidResult());
  }

  public void testScriptMain() throws Exception {
    PsiJavaReference ref = (PsiJavaReference) configureByFile("scriptMain/ABCF.java");
    JavaResolveResult resolveResult = ref.advancedResolve(false);
    assertTrue(resolveResult.getElement() instanceof GroovyScriptMethod);
    assertTrue(resolveResult.isValidResult());
  }

  public void testScriptMethod() throws Exception {
    PsiJavaReference ref = (PsiJavaReference) configureByFile("scriptMethod/ABCF.java");
    JavaResolveResult resolveResult = ref.advancedResolve(false);
    assertTrue(resolveResult.getElement() instanceof GrMethod);
    assertTrue(resolveResult.isValidResult());
  }

}