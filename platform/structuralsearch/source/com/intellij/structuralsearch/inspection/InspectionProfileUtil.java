// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.inspection;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModel;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.regexp.inspection.custom.CustomRegExpInspection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Bas Leijdekkers
 */
public final class InspectionProfileUtil {

  private InspectionProfileUtil() {}

  @NotNull
  public static SSBasedInspection getStructuralSearchInspection(@NotNull InspectionProfile profile) {
    return (SSBasedInspection)getInspection(profile, SSBasedInspection.SHORT_NAME);
  }

  public static CustomRegExpInspection getCustomRegExpInspection(@NotNull InspectionProfile profile) {
    return (CustomRegExpInspection)getInspection(profile, CustomRegExpInspection.SHORT_NAME);
  }

  public static InspectionProfileEntry getInspection(@NotNull InspectionProfile profile, @NonNls String shortName) {
    final InspectionToolWrapper<?, ?> wrapper = profile.getInspectionTool(shortName, (Project)null);
    assert wrapper != null;
    return wrapper.getTool();
  }

  public static InspectionProfileModifiableModel getInspectionProfile(@NotNull Component c) {
    final SingleInspectionProfilePanel panel = UIUtil.uiParents(c, true).filter(SingleInspectionProfilePanel.class).first();
    if (panel == null) return null;
    return panel.getProfile();
  }

  public static void fireProfileChanged(@NotNull InspectionProfileImpl profile) {
    profile.getProfileManager().fireProfileChanged(profile);
  }
}
