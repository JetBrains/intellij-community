package com.intellij.refactoring;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

public class MoveHtmlFileTest extends MoveFileTestCase {
  @Override
  protected @NotNull String getTestDataPath() {
    return PathManager.getCommunityHomePath() + "/plugins/htmltools/testData/refactoring/move/";
  }

  public void testHtml() { doTest("toDir", "B.html"); }

  public static class BranchTest extends MoveHtmlFileTest {
    @Override
    protected void setUp() throws Exception {
      super.setUp();
      Registry.get("run.refactorings.in.model.branch").setValue(true, getTestRootDisposable());
    }
  }
}
