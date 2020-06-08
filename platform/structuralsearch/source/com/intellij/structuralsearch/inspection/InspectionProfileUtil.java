// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.inspection;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModel;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import com.intellij.util.ui.UIUtil;

import java.awt.*;

/**
 * @author Bas Leijdekkers
 */
public final class InspectionProfileUtil {

  private InspectionProfileUtil() {}

  public static SSBasedInspection getStructuralSearchInspection(InspectionProfile profile) {
    final InspectionToolWrapper<?, ?> wrapper = profile.getInspectionTool(SSBasedInspection.SHORT_NAME, (Project)null);
    assert wrapper != null;
    return (SSBasedInspection)wrapper.getTool();
  }

  public static InspectionProfileModifiableModel getInspectionProfile(Component c) {
    final SingleInspectionProfilePanel panel = UIUtil.uiParents(c, true).filter(SingleInspectionProfilePanel.class).first();
    if (panel == null) return null;
    return panel.getProfile();
  }

  public static void fireProfileChanged(InspectionProfileImpl profile) {
    profile.getProfileManager().fireProfileChanged(profile);
  }
}
