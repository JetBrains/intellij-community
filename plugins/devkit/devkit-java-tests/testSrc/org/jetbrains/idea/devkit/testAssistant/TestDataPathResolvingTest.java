// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/resolveTestDataPath/")
public class TestDataPathResolvingTest extends TestDataPathTestCase {
  public void testProjectRootReference() {
    doTest(PsiManager.getInstance(myFixture.getProject()).findDirectory(PlatformTestUtil.getOrCreateProjectBaseDir(myFixture.getProject())));
  }

  public void testReferencesAfterProjectRoot() {
    doTest(PsiManager.getInstance(myFixture.getProject()).findDirectory(myProjectSubdir));
  }

  public void testContentRootReference() {
    doTest(PsiManager.getInstance(myFixture.getProject()).findDirectory(myContentRoot));
  }

  public void testReferencesAfterContentRoot() {
    doTest(PsiManager.getInstance(myFixture.getProject()).findDirectory(myContentRootSubdir));
  }

  private void doTest(@Nullable PsiFileSystemItem expectedResolve) {
    myFixture.configureByFile(getTestName(false) + ".java");
    final PsiReference referenceAt = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
    assertNotNull(referenceAt);
    assertEquals(expectedResolve, referenceAt.resolve());
  }

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "resolveTestDataPath";
  }
}
