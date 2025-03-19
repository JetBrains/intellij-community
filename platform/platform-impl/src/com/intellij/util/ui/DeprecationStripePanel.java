// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.impl.welcomeScreen.BottomLineBorder;
import com.intellij.ui.components.AnActionLink;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

@ApiStatus.Internal
public final class DeprecationStripePanel extends JPanel {
  public DeprecationStripePanel(@NlsContexts.Label @NotNull String mainText, @Nullable Icon icon) {
    super(new FlowLayout(FlowLayout.CENTER));
    setBorder(new BottomLineBorder());
    setBackground(EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.NOTIFICATION_BACKGROUND));
    add(new JLabel(mainText, icon, SwingConstants.LEADING));
  }

  public DeprecationStripePanel withAlternativeAction(@NotNull @NlsContexts.LinkLabel String linkText, @NotNull AnAction action) {
    add(new AnActionLink(linkText, action));
    return this;
  }

  public @NotNull JPanel wrap(@NotNull JComponent mainComponent) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(this, BorderLayout.NORTH);
    panel.add(mainComponent, BorderLayout.CENTER);
    return panel;
  }
}
