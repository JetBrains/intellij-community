// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.frame;

import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;

/**
 * Describes a hyperlink inside a debugger node
 */
public abstract class XDebuggerTreeNodeHyperlink {
  public static final SimpleTextAttributes TEXT_ATTRIBUTES = new SimpleTextAttributes(
    SimpleTextAttributes.STYLE_PLAIN, JBUI.CurrentTheme.Link.linkSecondaryColor());

  private final String linkText;

  protected XDebuggerTreeNodeHyperlink(@NotNull String linkText) {
    this.linkText = linkText;
  }

  @NotNull
  public String getLinkText() {
    return linkText;
  }

  @NotNull
  public SimpleTextAttributes getTextAttributes() {
    return TEXT_ATTRIBUTES;
  }

  public abstract void onClick(MouseEvent event);

  public boolean alwaysOnScreen() {
    return false;
  }
}
