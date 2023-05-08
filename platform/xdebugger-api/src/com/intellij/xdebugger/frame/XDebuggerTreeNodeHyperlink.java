// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.frame;

import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;

/**
 * Describes a hyperlink inside a debugger node
 */
public abstract class XDebuggerTreeNodeHyperlink {
  public static final SimpleTextAttributes TEXT_ATTRIBUTES = new SimpleTextAttributes(
    SimpleTextAttributes.STYLE_PLAIN, JBUI.CurrentTheme.Link.Foreground.SECONDARY);

  @Nls
  private final String linkText;
  
  @Nullable
  @Nls
  private final String linkTooltip;

  @Nullable
  private final Icon linkIcon;
  
  private final boolean isClickedOnEnter;

  protected XDebuggerTreeNodeHyperlink(@NotNull @Nls String linkText) {
    this(linkText, null, null, false);
  }
  
  protected XDebuggerTreeNodeHyperlink(@NotNull @Nls String linkText, @Nullable XFullValueEvaluator.LinkAttributes linkAttributes) {
    this(
      linkText,
      linkAttributes != null ? linkAttributes.getLinkTooltipText() : null,
      linkAttributes != null ? linkAttributes.getLinkIcon() : null,
      linkAttributes != null && linkAttributes.isClickedOnEnter()
    );
  }

  private XDebuggerTreeNodeHyperlink(
    @NotNull @Nls String linkText,
    @Nullable @Nls String toolTipText,
    @Nullable Icon icon,
    boolean isExpandedOnEnter
  ) {
    this.linkText = linkText;
    this.linkTooltip = toolTipText;
    this.linkIcon = icon;
    this.isClickedOnEnter = isExpandedOnEnter;
  }

  @NotNull
  @Nls
  public String getLinkText() {
    return linkText;
  }

  @Nullable
  @Nls
  public String getLinkTooltip() {
    return linkTooltip;
  }

  @Nullable
  public Icon getLinkIcon() {
    return linkIcon;
  }

  public boolean getIsClickedOnEnter() {
    return isClickedOnEnter;
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
