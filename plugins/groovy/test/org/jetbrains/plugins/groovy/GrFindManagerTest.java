/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.find.FindManager;
import com.intellij.find.FindManagerTestUtils;
import com.intellij.find.FindModel;

public class GrFindManagerTest extends DaemonAnalyzerTestCase {
  private FindManager myFindManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFindManager = FindManager.getInstance(myProject);
  }

  @Override
  protected void tearDown() throws Exception {
    myFindManager = null;
    super.tearDown();
  }

  public void testFindInJavaDocs() {
    FindModel findModel = FindManagerTestUtils.configureFindModel("done");
    String text = "/** done done done */";
    FindManagerTestUtils.runFindForwardAndBackward(myFindManager, findModel, text, "groovy");
  }

  public void testFindInLiteralToSkipQuotes() {
    FindModel findModel = FindManagerTestUtils.configureFindModel("^done$");
    findModel.setRegularExpressions(true);
    findModel.setInStringLiteralsOnly(true);
    findModel.setInCommentsOnly(false);
    String text = "def n = \"\"\"done\"\"\"\n def n = /done/\n def n = \"done\"\n def n = \"done2\"";
    FindManagerTestUtils.runFindForwardAndBackward(myFindManager, findModel, text, "groovy");
  }

  public void testFindInShellCommentsOfGroovy() {
    FindModel findModel = FindManagerTestUtils.configureFindModel("done");
    findModel.setWholeWordsOnly(true);
    findModel.setInCommentsOnly(true);
    String text = "#! done done done\n";
    FindManagerTestUtils.runFindForwardAndBackward(myFindManager, findModel, text, "groovy");
  }
}
