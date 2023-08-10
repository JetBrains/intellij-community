// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.ToolWindowAnchor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public final class StaticAnchoredButton extends AnchoredButton {
  @NotNull
  private ToolWindowAnchor myToolWindowAnchor;
  private int myMnemonic2;

  public StaticAnchoredButton(@NlsContexts.Button String text,
                              Icon icon,
                              boolean selected,
                              @NotNull ToolWindowAnchor toolWindowAnchor) {
    super(text, icon, selected);
    myToolWindowAnchor = toolWindowAnchor;
    init();
  }

  public StaticAnchoredButton(@NlsContexts.Button String text, Icon icon, @NotNull ToolWindowAnchor toolWindowAnchor) {
    super(text, icon);
    myToolWindowAnchor = toolWindowAnchor;
    init();
  }

  public StaticAnchoredButton(Action a, @NotNull ToolWindowAnchor toolWindowAnchor) {
    super(a);
    myToolWindowAnchor = toolWindowAnchor;
    init();
  }

  public StaticAnchoredButton(@NlsContexts.Button String text, boolean selected, @NotNull ToolWindowAnchor toolWindowAnchor) {
    super(text, selected);
    myToolWindowAnchor = toolWindowAnchor;
    init();
  }

  public StaticAnchoredButton(@NlsContexts.Button String text, @NotNull ToolWindowAnchor toolWindowAnchor) {
    super(text);
    myToolWindowAnchor = toolWindowAnchor;
    init();
  }

  public StaticAnchoredButton(Icon icon, boolean selected, @NotNull ToolWindowAnchor toolWindowAnchor) {
    super(icon, selected);
    myToolWindowAnchor = toolWindowAnchor;
    init();
  }

  public StaticAnchoredButton(Icon icon, @NotNull ToolWindowAnchor toolWindowAnchor) {
    super(icon);
    myToolWindowAnchor = toolWindowAnchor;
    init();
  }

  public StaticAnchoredButton(@NotNull ToolWindowAnchor toolWindowAnchor) {
    myToolWindowAnchor = toolWindowAnchor;
    init();
  }

  private void init() {
    setFocusable(false);
//    setBackground(ourBackgroundColor);
    final Border border = BorderFactory.createEmptyBorder(5, 5, 0, 5);
    setBorder(border);
    setRolloverEnabled(true);
    setOpaque(false);
    enableEvents(AWTEvent.MOUSE_EVENT_MASK);
  }

  @Override
  public int getMnemonic2() {
    return myMnemonic2;
  }

  @Override
  public ToolWindowAnchor getAnchor() {
    return myToolWindowAnchor;
  }

  public void setToolWindowAnchor(@NotNull ToolWindowAnchor toolWindowAnchor) {
    myToolWindowAnchor = toolWindowAnchor;
  }

  public void setMnemonic2(int mnemonic2) {
    myMnemonic2 = mnemonic2;
  }
}
