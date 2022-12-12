// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.PluginPathManager;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SizeReplaceableByIsEmptyFixTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new SizeReplaceableByIsEmptyInspection()};
  }

  @Override
  protected String getBasePath() {
    return "/com/siyeh/igtest/style/size_replaceable_by_is_empty";
  }

  @Override
  protected @NotNull String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("InspectionGadgets") + "/test";
  }
}