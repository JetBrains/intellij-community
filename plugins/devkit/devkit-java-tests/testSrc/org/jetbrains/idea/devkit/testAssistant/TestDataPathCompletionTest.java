/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.TestDataPath;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

import java.util.Arrays;

@TestDataPath("$CONTENT_ROOT/testData/completeTestDataPath")
public class TestDataPathCompletionTest extends TestDataPathTestCase {
  private static final Logger LOG = Logger.getInstance(TestDataPathCompletionTest.class);

  public void testProjectRoot() {
    doTest();
  }

  public void testReferencesAfterProjectRoot() {
    doTest();
  }

  public void testContentRoot() {
    doTest();
  }

  public void testReferencesAfterContentRoot() {
    doTest();
  }

  private void doTest() {
    String testName = getTestName(false);
    LOG.debug("Executing test: " + testName);

    PsiFile file = myFixture.configureByFile(testName + ".java");
    LOG.debug("PsiFile: " + file);
    LOG.debug("PsiFile#getVirtualFile: " + file.getVirtualFile());

    LookupElement[] elements = myFixture.completeBasic();
    LOG.debug("Lookup elements: " + Arrays.toString(elements));

    myFixture.checkResultByFile(testName + "_after.java");
  }

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "completeTestDataPath";
  }
}
