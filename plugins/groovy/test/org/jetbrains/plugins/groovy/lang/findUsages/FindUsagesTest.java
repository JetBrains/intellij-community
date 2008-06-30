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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiReferenceProcessor;
import com.intellij.psi.search.PsiReferenceProcessorAdapter;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.Query;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.ArrayList;

/**
 * @author ven
 */
public class FindUsagesTest extends IdeaTestCase {
  protected CodeInsightTestFixture myFixture;

  protected void setUp() throws Exception {
    super.setUp();

    final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
    final TestFixtureBuilder<IdeaProjectTestFixture> builder = fixtureFactory.createFixtureBuilder();
    myFixture = fixtureFactory.createCodeInsightFixture(builder.getFixture());
    builder.addModule(JavaModuleFixtureBuilder.class).addJdk(TestUtils.getMockJdkHome()).addContentRoot(TestUtils.getTestDataPath() + "/findUsages" + "/" + getTestName(true)).addSourceRoot("");
    myFixture.setTestDataPath(TestUtils.getTestDataPath() + "/findUsages");
    myFixture.setUp();

    ApplicationManager.getApplication().runWriteAction(
            new Runnable() {
              public void run() {
                try {
                  String root = TestUtils.getTestDataPath() + "/findUsages" + "/" + getTestName(true);
                  PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);
                }
                catch (Exception e) {
                  LOG.error(e);
                }
              }
            }
    );
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
    myFixture.configureByFile("derivedClass/p/B.java");
    final PsiElement elementAt = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    final PsiClass clazz = PsiTreeUtil.getParentOfType(elementAt, PsiClass.class);
    assertNotNull(clazz);

    final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myFixture.getProject());
    final Query<PsiClass> query = DirectClassInheritorsSearch.search(clazz, projectScope);

    assertEquals(1, query.findAll().size());
  }

  public void testConstructor1() throws Throwable {
    doConstructorTest("constructor1/A.groovy", 2);
  }

  public void testSetter1() throws Throwable {
    doTestImpl("setter1/A.groovy", 2);
  }

  public void testGetter1() throws Throwable {
    doTestImpl("getter1/A.groovy", 1);
  }

  public void testProperty1() throws Throwable {
    doTestImpl("property1/A.groovy", 1);
  }

  public void testProperty2() throws Throwable {
    myFixture.configureByFile("property2/A.groovy");
    final PsiElement elementAt = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    final GrField field = PsiTreeUtil.getParentOfType(elementAt, GrField.class);
    assertNotNull(field);
    doFind(1, field);
  }

  public void testEscapedReference() throws Throwable {
    doTestImpl("escapedReference/A.groovy", 1);
  }

  public void testKeywordPropertyName() throws Throwable {
    doTestImpl("keywordPropertyName/A.groovy", 1);
  }

  public void testTypeAlias() throws Throwable {
    doTestImpl("typeAlias/A.groovy", 2);
  }

  public void testForInParameter() throws Throwable {
    doTestImpl("forInParameter/A.groovy", 1);
  }

  public void testSyntheticParameter() throws Throwable {
    doTestImpl("syntheticParameter/A.groovy", 1);
  }


  private void doTestImpl(String filePath, int expectedUsagesCount) throws Throwable {

    myFixture.configureByFile(filePath);
    int offset = myFixture.getEditor().getCaretModel().getOffset();
    final PsiReference ref = myFixture.getFile().findReferenceAt(offset);
    assertNotNull("Did not find reference", ref);
    final PsiElement resolved = ref.resolve();
    assertNotNull("Could not resolve reference", resolved);

    doTest(resolved);


    System.out.println("preved");
//    doFind(expectedUsagesCount, resolved);
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

  public void testAnnotatedMemberSearch() throws Throwable {

    final PsiReference ref = myFixture.getReferenceAtCaretPosition("annotatedMemberSearch/A.groovy");
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


  private void doTest(PsiElement element) throws Exception {
    final ArrayList<PsiFile> filesList = new ArrayList<PsiFile>();
    ReferencesSearch.search(element, GlobalSearchScope.projectScope(myProject), false).forEach(new PsiReferenceProcessorAdapter(new PsiReferenceProcessor() {
      public boolean execute(PsiReference ref) {
        addReference(ref, filesList);
        return true;
      }
    }));

    System.out.println("preved");

//    checkResult(fileNames, filesList, starts, startsList, ends, endsList);

  }

  private void addReference(PsiReference ref, ArrayList<PsiFile> filesList) {
    PsiElement element = ref.getElement();
    filesList.add(element.getContainingFile());
  }

  private void doTest1(PsiMethod method) {
    final ArrayList<PsiFile> filesList = new ArrayList<PsiFile>();
    PsiReference[] refs =
            MethodReferencesSearch.search(method, GlobalSearchScope.projectScope(myProject), false).toArray(PsiReference.EMPTY_ARRAY);
    for (PsiReference ref : refs) {
      addReference(ref, filesList);
    }
//    checkResult(fileNames, filesList, starts, startsList, ends, endsList);
  }
}