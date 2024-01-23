// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.inspection;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModel;
import com.intellij.profile.codeInspection.ui.CustomInspectionActions;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Bas Leijdekkers
 */
public final class InspectionProfileUtil {

  private InspectionProfileUtil() {}

  public static InspectionProfileModifiableModel getInspectionProfile(@NotNull Component c) {
    final SingleInspectionProfilePanel panel = UIUtil.uiParents(c, true).filter(SingleInspectionProfilePanel.class).first();
    if (panel == null) return null;
    return panel.getProfile();
  }

  public static String[] getGroup() {
    return new String[] {InspectionsBundle.message("group.names.user.defined"), SSRBundle.message("structural.search.group.name")};
  }

  /**
   * @deprecated Use {@link CustomInspectionActions#fireProfileChanged(InspectionProfileImpl)}.
   */
  @Deprecated
  public static void fireProfileChanged(@NotNull InspectionProfileImpl profile) {
    CustomInspectionActions.fireProfileChanged(profile);
  }
}
