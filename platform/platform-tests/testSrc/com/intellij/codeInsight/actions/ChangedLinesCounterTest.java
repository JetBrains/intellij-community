/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
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
package com.intellij.codeInsight.actions;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.junit.Assert;

import java.io.File;

public class ChangedLinesCounterTest extends LightPlatformCodeInsightFixtureTestCase {

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/')
           + "/platform/platform-tests/testData/"
           + "codeStyle/formatter/";
  }

  @Override
  public void tearDown() throws Exception {
    myFixture.getFile().putUserData(FormatChangedTextUtil.TEST_REVISION_CONTENT, null);
    super.tearDown();
  }

  public void doTest(int expectedLinesChanged) {
    myFixture.configureByFile(getTestName(true) + "_revision.java");
    PsiFile file = myFixture.getFile();
    CharSequence revisionContent = myFixture.getDocument(file).getCharsSequence();

    myFixture.configureByFile(getTestName(true) + ".java");
    file = myFixture.getFile();
    Document document = myFixture.getDocument(file);

    int linesChanged = FormatChangedTextUtil.getInstance().calculateChangedLinesNumber(document, revisionContent);
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
