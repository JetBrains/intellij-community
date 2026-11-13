// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy;

import com.intellij.find.FindManager;
import com.intellij.find.FindManagerTestUtils;
import com.intellij.find.FindModel;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;


@TestApplication
public class GrFindManagerTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture();

  @Test
  public void testFindInJavaDocs() {
    FindModel findModel = FindManagerTestUtils.configureFindModel("done");
    String text = "/** done done done */";
    runFindForwardAndBackward(findModel, text);
  }

  @Test
  public void testFindInLiteralToSkipQuotes() {
    FindModel findModel = FindManagerTestUtils.configureFindModel("^done$");
    findModel.setRegularExpressions(true);
    findModel.setSearchContext(FindModel.SearchContext.IN_STRING_LITERALS);
    String text = "def n = \"\"\"done\"\"\"\n def n = /done/\n def n = \"done\"\n def n = \"done2\"";
    runFindForwardAndBackward(findModel, text);
  }

  @Test
  public void testFindInShellCommentsOfGroovy() {
    FindModel findModel = FindManagerTestUtils.configureFindModel("done");
    findModel.setWholeWordsOnly(true);
    findModel.setSearchContext(FindModel.SearchContext.IN_COMMENTS);
    String text = "#! done done done\n";
    runFindForwardAndBackward(findModel, text);
  }

  private static void runFindForwardAndBackward(FindModel findModel, String text) {
    FindManager findManager = FindManager.getInstance(project.get().getProject());
    EdtTestUtil.runInEdtAndWait(() -> FindManagerTestUtils.runFindForwardAndBackward(findManager, findModel, text, "groovy"));
  }
}
