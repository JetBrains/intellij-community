package com.intellij.refactoring;

import com.intellij.openapi.application.PathManager;
import org.jetbrains.annotations.NotNull;

public class MoveHtmlFileTest extends MoveFileTestCase {
  @Override
  protected @NotNull String getTestDataPath() {
    return PathManager.getCommunityHomePath() + "/plugins/htmltools/testData/refactoring/move/";
  }

  public void testHtml() { doTest("toDir", "B.html"); }
}
