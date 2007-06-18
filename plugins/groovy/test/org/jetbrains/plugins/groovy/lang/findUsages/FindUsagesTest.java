/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.findUsages;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
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
    builder.addModule(JavaModuleFixtureBuilder.class).addContentRoot(myFixture.getTempDirPath());
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

  private void doTest(String filePath, int expectedUsagesCount) throws Throwable {

    final PsiReference ref = myFixture.getReferenceAtCaretPosition(filePath);
    assertNotNull(ref);
    final PsiElement resolved = ref.resolve();
    assertNotNull(resolved);

    final Query<PsiReference> query;
    if (resolved instanceof PsiMethod) {
      query = MethodReferencesSearch.search((PsiMethod) resolved);
    }
    else {
      query = ReferencesSearch.search(resolved);
    }

    assertEquals(expectedUsagesCount, query.findAll().size());
  }

}