// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.junit.Assert;

public class ChangedLinesCounterTest extends BasePlatformTestCase {

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getPlatformTestDataPath()
           + "codeStyle/formatter/";
  }

  @Override
  public void tearDown() throws Exception {
    try {
      PsiFile file = myFixture.getFile();
      if (file != null) {
        file.putUserData(VcsFacade.TEST_REVISION_CONTENT, null);
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void doTest(int expectedLinesChanged) {
    myFixture.configureByFile(getTestName(true) + "_revision.java");
    PsiFile file = myFixture.getFile();
    CharSequence revisionContent = myFixture.getDocument(file).getCharsSequence();

    myFixture.configureByFile(getTestName(true) + ".java");
    file = myFixture.getFile();
    Document document = myFixture.getDocument(file);

    int linesChanged = VcsFacade.getInstance().calculateChangedLinesNumber(document, revisionContent);
    Assert.assertTrue(linesChanged > 0);
    Assert.assertEquals(expectedLinesChanged, linesChanged);
  }

  public void testAddedLines() {
    doTest(17);
  }

  public void testModifiedLines() {
    doTest(6);
  }

  public void testDeletedLines() {
    doTest(3);
  }

  public void testChangedSingleLine() {
    doTest(1);
  }

  public void testChangedAndDeleted() {
    doTest(6);
  }

  public void testModification() {
    doTest(3);
  }

  public void testInsert() {
    doTest(2);
  }

  public void testLotsWhiteSpaces() {
    doTest(19);
  }
}
