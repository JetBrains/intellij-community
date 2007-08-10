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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.Query;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @uthor ven
 */
public class FindUsagesTest extends IdeaTestCase {
  protected CodeInsightTestFixture myFixture;

  protected void setUp() throws Exception {
    super.setUp();

    final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
    final TestFixtureBuilder<IdeaProjectTestFixture> builder = fixtureFactory.createFixtureBuilder();
    myFixture = fixtureFactory.createCodeInsightFixture(builder.getFixture());
    builder.addModule(JavaModuleFixtureBuilder.class).addJdk(TestUtils.getMockJdkHome()).addContentRoot(myFixture.getTempDirPath()+ "/" + getTestName(true)).addSourceRoot("");
    myFixture.setTestDataPath(TestUtils.getTestDataPath() + "/findUsages");
    myFixture.setUp();

    GroovyPsiManager.getInstance(myFixture.getProject()).buildGDK();
  }

  protected void tearDown() throws Exception {
    myFixture.tearDown();
    myFixture = null;
    super.tearDown();
  }

  public void testSetter1() throws Throwable {
    doTest("setter1/A.groovy", 2);
  }

  public void testGetter1() throws Throwable {
    doTest("getter1/A.groovy", 1);
  }

  public void testProperty1() throws Throwable {
    doTest("property1/A.groovy", 2);
  }

  public void testEscapedReference() throws Throwable {
    doTest("escapedReference/A.groovy", 1);
  }

  public void testKeywordPropertyName() throws Throwable {
    doTest("keywordPropertyName/A.groovy", 1);
  }

  public void testTypeAlias() throws Throwable {
    doTest("typeAlias/A.groovy", 2);
  }

  public void testSytheticParameter() throws Throwable {
    doTest("syntheticParameter/A.groovy", 1);
  }


  private void doTest(String filePath, int expectedUsagesCount) throws Throwable {

    final PsiReference ref = myFixture.getReferenceAtCaretPosition(filePath);
    assertNotNull("Did not find reference", ref);
    final PsiElement resolved = ref.resolve();
    assertNotNull("Could not resolve reference", resolved);

    final Query<PsiReference> query;
    final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myFixture.getProject());
    if (resolved instanceof PsiMethod) {
      query = MethodReferencesSearch.search((PsiMethod) resolved, projectScope, true);
    }
    else {
      query = ReferencesSearch.search(resolved, projectScope);
    }

    assertEquals(expectedUsagesCount, query.findAll().size());
  }

}