/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.findUsages;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Query;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.Collection;
import java.util.Iterator;

/**
 * @author ven
 */
public class FindUsagesTest extends LightGroovyTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "findUsages/" + getTestName(true) + "/";
  }

  private void doConstructorTest(String filePath, int expectedCount) throws Throwable {
    myFixture.configureByFile(filePath);
    final PsiElement elementAt = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    final PsiMethod method = PsiTreeUtil.getParentOfType(elementAt, PsiMethod.class);
    assertNotNull(method);
    assertTrue(method.isConstructor());
    final Query<PsiReference> query = ReferencesSearch.search(method);

    assertEquals(expectedCount, query.findAll().size());
  }

  public void testDerivedClass() throws Throwable {
    myFixture.configureByFiles("p/B.java", "A.groovy");
    final PsiElement elementAt = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    final PsiClass clazz = PsiTreeUtil.getParentOfType(elementAt, PsiClass.class);
    assertNotNull(clazz);

    final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myFixture.getProject());
    final Query<PsiClass> query = DirectClassInheritorsSearch.search(clazz, projectScope);

    assertEquals(1, query.findAll().size());
  }

  public void testConstructor1() throws Throwable {
    doConstructorTest("A.groovy", 2);
  }

  public void testConstructorUsageInNewExpression() throws Throwable {
    doTestImpl("ConstructorUsageInNewExpression.groovy", 3);
  }

  public void testGotoConstructor() throws Throwable {
    myFixture.configureByFile("GotoConstructor.groovy");
    final TargetElementUtilBase utilBase = TargetElementUtilBase.getInstance();
    final PsiElement target = utilBase.findTargetElement(myFixture.getEditor(), utilBase.getReferenceSearchFlags());
    assertNotNull(target);
    assertInstanceOf(target, PsiMethod.class);
    assertTrue(((PsiMethod)target).isConstructor());
    assertTrue(((PsiMethod)target).getParameterList().getParametersCount() == 0);
  }

  public void testSetter1() throws Throwable {
    doTestImpl("A.groovy", 2);
  }

  public void testGetter1() throws Throwable {
    doTestImpl("A.groovy", 1);
  }

  public void testProperty1() throws Throwable {
    doTestImpl("A.groovy", 1);
  }

  //todo [ilyas]
  public void _testProperty2() throws Throwable {
    doTestImpl("A.groovy", 1);
  }

  public void testEscapedReference() throws Throwable {
    doTestImpl("A.groovy", 1);
  }

  public void testKeywordPropertyName() throws Throwable {
    doTestImpl("A.groovy", 1);
  }

  public void testTypeAlias() throws Throwable {
    doTestImpl("A.groovy", 2);
  }

  public void testMethodAlias() throws Throwable {
    doTestImpl("A.groovy", 2);
  }

  public void testAliasImportedProperty() throws Throwable {
    myFixture.addFileToProject("Abc.groovy", "class Abc {static def foo}");
    doTestImpl("A.groovy", 1);
  }

  public void testGetterWhenAliasedImportedProperty() throws Throwable {
    myFixture.addFileToProject("Abc.groovy", "class Abc {static def foo}");
    doTestImpl("A.groovy", 2);
  }

  public void testForInParameter() throws Throwable {
    doTestImpl("A.groovy", 1);
  }

  public void testSyntheticParameter() throws Throwable {
    doTestImpl("A.groovy", 1);
  }

  public void testAnnotatedMemberSearch() throws Throwable {

    final PsiReference ref = myFixture.getReferenceAtCaretPosition("A.groovy");
    assertNotNull("Did not find reference", ref);
    final PsiElement resolved = ref.resolve();
    assertNotNull("Could not resolve reference", resolved);

    final Query<PsiReference> query;
    final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myFixture.getProject());
    if (resolved instanceof PsiMethod) {
      query = MethodReferencesSearch.search((PsiMethod)resolved, projectScope, true);
    }
    else {
      query = ReferencesSearch.search(resolved, projectScope);
    }

    assertEquals(1, query.findAll().size());
  }

  private void doTestImpl(String filePath, int expectedUsagesCount) throws Throwable {
    myFixture.configureByFile(filePath);
    int offset = myFixture.getEditor().getCaretModel().getOffset();
    final PsiReference ref = myFixture.getFile().findReferenceAt(offset);
    assertNotNull("Did not find reference", ref);
    final PsiElement resolved = ref.resolve();
    assertNotNull("Could not resolve reference", resolved);
    doFind(expectedUsagesCount, resolved);
  }

  private void doFind(int expectedUsagesCount, PsiElement resolved) {
    final Query<PsiReference> query;
    final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myFixture.getProject());
    if (resolved instanceof PsiMethod) {
      query = MethodReferencesSearch.search((PsiMethod)resolved, projectScope, true);
    }
    else {
      query = ReferencesSearch.search(resolved, projectScope);
    }

    Collection<PsiReference> references = query.findAll();
    assertEquals(expectedUsagesCount, references.size());
  }

  public void testGDKSuperMethodSearch() throws Exception {
    doSuperMethodTest("Object");
  }

  public void testGDKSuperMethodForMapSearch() throws Exception {
    doSuperMethodTest("Map");
  }

  public void testLabels() throws Exception {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    final GroovyFile file = (GroovyFile)myFixture.getFile();
    assertEquals(2, ReferencesSearch.search(file.getTopStatements()[0]).findAll().size());
  }

  private void doSuperMethodTest(String... firstParameterTypes) throws Exception {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    final GroovyFile file = (GroovyFile)myFixture.getFile();
    final GrTypeDefinition psiClass = (GrTypeDefinition)file.getClasses()[0];
    final GrMethod method = (GrMethod)psiClass.getMethods()[0];
    final Collection<MethodSignatureBackedByPsiMethod> superMethods = SuperMethodsSearch.search(method, null, true, true).findAll();
    assertEquals(firstParameterTypes.length, superMethods.size());

    final Iterator<MethodSignatureBackedByPsiMethod> iterator = superMethods.iterator();
    for (String firstParameterType : firstParameterTypes) {
      final MethodSignatureBackedByPsiMethod methodSignature = iterator.next();
      final PsiMethod superMethod = methodSignature.getMethod();
      final String className = superMethod.getContainingClass().getName();
      assertEquals("DefaultGroovyMethods", className);
      final String actualParameterType = ((PsiClassType)methodSignature.getParameterTypes()[0]).resolve().getName();
      assertEquals(firstParameterType, actualParameterType);
    }
  }

}