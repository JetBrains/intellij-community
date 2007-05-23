/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.testFramework.ResolveTestCase;
import org.jetbrains.plugins.groovy.GroovyLoader;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.DefaultGroovyMethod;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author ven
 */
public class ResolveMethodTest extends GroovyResolveTestCase {

  protected String getTestDataPath() {
    return TestUtils.getTestDataPath() + "/resolve/method/";
  }

  public void testSimple() throws Exception {
    PsiReference ref = configureByFile("simple/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertEquals(((GrMethod) resolved).getParameters().length, 1);
  }

  public void testVarargs() throws Exception {
    PsiReference ref = configureByFile("varargs/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertEquals(((GrMethod) resolved).getParameters().length, 1);
  }

  public void testByName() throws Exception {
    PsiReference ref = configureByFile("byName/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
  }

  public void testByName1() throws Exception {
    PsiReference ref = configureByFile("byName1/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertEquals(((GrMethod) resolved).getParameters().length, 2);
  }

  public void testByNameVarargs() throws Exception {
    PsiReference ref = configureByFile("byNameVarargs/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertEquals(((GrMethod) resolved).getParameters().length, 1);
  }

  public void testParametersNumber() throws Exception {
    PsiReference ref = configureByFile("parametersNumber/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertEquals(((GrMethod) resolved).getParameters().length, 2);
  }

  public void testFilterBase() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("filterBase/A.groovy");
    assertNotNull(ref.resolve());
    assertEquals(1, ref.multiResolve(false).length);
  }

  public void testTwoCandiidates() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("twoCandidates/A.groovy");
    assertNull(ref.resolve());
    assertEquals(2, ref.multiResolve(false).length);
  }

  public void testDefaultMethod1() throws Exception {
    PsiReference ref = configureByFile("defaultMethod1/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof DefaultGroovyMethod);
  }

  public void testDefaultMethod2() throws Exception {
    PsiReference ref = configureByFile("defaultMethod2/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertTrue(resolved instanceof DefaultGroovyMethod);
  }

  public void testScriptMethod() throws Exception {
   PsiReference ref = configureByFile("scriptMethod/A.groovy");
   PsiElement resolved = ref.resolve();
   assertTrue(resolved instanceof PsiMethod);
   assertEquals("groovy.lang.Script", ((PsiMethod) resolved).getContainingClass().getQualifiedName());
 }
}
