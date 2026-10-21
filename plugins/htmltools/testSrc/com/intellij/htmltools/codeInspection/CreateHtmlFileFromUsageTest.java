package com.intellij.htmltools.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.HtmlUnknownTargetInspection;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.openapi.application.PathManager;
import org.jetbrains.annotations.NotNull;

public class CreateHtmlFileFromUsageTest extends LightQuickFixParameterizedTestCase {
  @Override
  public @NotNull String getTestDataPath() {
    return PathManager.getCommunityHomePath() + "/plugins/htmltools/testData";
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[] {new HtmlUnknownTargetInspection()};
  }


  @Override
  protected String getBasePath() {
    return "/quickFix/createFileFromUsage";
  }

}
