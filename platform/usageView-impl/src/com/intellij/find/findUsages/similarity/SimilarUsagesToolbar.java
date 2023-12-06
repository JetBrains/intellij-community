// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages.similarity;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.ActionLink;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.actionSystem.ActionPlaces.SIMILAR_USAGES_PREVIEW_TOOLBAR;

public class SimilarUsagesToolbar extends JPanel {
  public SimilarUsagesToolbar(@NotNull JComponent targetComponent,
                              @Nls String text,
                              @Nullable AnAction refreshAction,
                              @NotNull ActionLink backActionLink) {
    super(new FlowLayout(FlowLayout.LEFT));
    setBackground(UIUtil.getTextFieldBackground());
    SimpleColoredComponent resultsText = new SimpleColoredComponent();
    resultsText.append(text, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    add(resultsText);
    if (refreshAction != null) {
      createActionGroupWithRefreshAction(targetComponent, refreshAction);
    }
    add(backActionLink);
  }

  private void createActionGroupWithRefreshAction(@NotNull JComponent targetComponent, @NotNull AnAction refreshAction) {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(refreshAction);
    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(SIMILAR_USAGES_PREVIEW_TOOLBAR, actionGroup, true);
    actionToolbar.getComponent().setBackground(UIUtil.getTextFieldBackground());
    actionToolbar.setTargetComponent(targetComponent);
    add(actionToolbar.getComponent());
  }
}
