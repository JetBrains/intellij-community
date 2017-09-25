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

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.Nullable;

@TestDataPath("$CONTENT_ROOT/testData/resolveTestDataPath/")
public class TestDataPathResolvingTest extends TestDataPathTestCase {
  public void testProjectRootReference() {
    doTest(PsiManager.getInstance(myFixture.getProject()).findDirectory(myFixture.getProject().getBaseDir()));
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
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/resolveTestDataPath";
  }
}
