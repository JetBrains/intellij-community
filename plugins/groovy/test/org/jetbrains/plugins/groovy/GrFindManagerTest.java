// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    findModel.setSearchContext(FindModel.SearchContext.IN_STRING_LITERALS);
    String text = "def n = \"\"\"done\"\"\"\n def n = /done/\n def n = \"done\"\n def n = \"done2\"";
    FindManagerTestUtils.runFindForwardAndBackward(myFindManager, findModel, text, "groovy");
  }

  public void testFindInShellCommentsOfGroovy() {
    FindModel findModel = FindManagerTestUtils.configureFindModel("done");
    findModel.setWholeWordsOnly(true);
    findModel.setSearchContext(FindModel.SearchContext.IN_COMMENTS);
    String text = "#! done done done\n";
    FindManagerTestUtils.runFindForwardAndBackward(myFindManager, findModel, text, "groovy");
  }
}
