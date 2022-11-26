// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.PluginPathManager;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryParenthesesFixTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new UnnecessaryParenthesesInspection()};
  }

  @Override
  protected String getBasePath() {
    return "/com/siyeh/igtest/style/unnecessary_parentheses";
  }

  @Override
  protected @NotNull String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("InspectionGadgets") + "/test";
  }

}