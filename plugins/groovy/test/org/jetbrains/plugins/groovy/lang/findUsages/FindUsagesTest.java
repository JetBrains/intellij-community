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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.Query;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author ven
 */
public class FindUsagesTest extends UsefulTestCase {
  protected CodeInsightTestFixture myFixture;

  protected void setUp() throws Exception {
    super.setUp();
    final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
    final TestFixtureBuilder<IdeaProjectTestFixture> builder = fixtureFactory.createFixtureBuilder();
    myFixture = fixtureFactory.createCodeInsightFixture(builder.getFixture());
    final JavaModuleFixtureBuilder moduleBuilder = builder.addModule(JavaModuleFixtureBuilder.class);
    moduleBuilder.addJdk(TestUtils.getMockJdkHome());
    myFixture.setTestDataPath(TestUtils.getTestDataPath() + "/findUsages" + "/" + getTestName(true));
    moduleBuilder.addContentRoot(myFixture.getTempDirPath()).addSourceRoot("");
    myFixture.setUp();
  }

  protected void tearDown() throws Exception {
    myFixture.tearDown();
    myFixture = null;
    super.tearDown();
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

  //todo [ilyas]
  public void _testConstructor1() throws Throwable {
    doConstructorTest("A.groovy", 2);
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
    doTestImpl("A.groovy", 1);
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
      query = MethodReferencesSearch.search((PsiMethod) resolved, projectScope, true);
    } else {
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
      query = MethodReferencesSearch.search((PsiMethod) resolved, projectScope, true);
    } else {
      query = ReferencesSearch.search(resolved, projectScope);
    }

    assertEquals(expectedUsagesCount, query.findAll().size());
  }

}