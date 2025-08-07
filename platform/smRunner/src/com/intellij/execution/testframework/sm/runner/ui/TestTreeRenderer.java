// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTRunnerNodeDescriptor;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.RelativeFont;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.*;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

public class TestTreeRenderer extends ColoredTreeCellRenderer {
  private static final @NonNls String SPACE_STRING = " ";

  private final TestConsoleProperties myConsoleProperties;
  private SMRootTestProxyFormatter myAdditionalRootFormatter;
  private String myDurationText;
  private Color myDurationColor;
  private int myDurationWidth;
  private int myDurationLeftInset;
  private int myDurationRightInset;

  private @Nullable Computable<String> myAccessibleStatus = null;

  public TestTreeRenderer(final TestConsoleProperties consoleProperties) {
    myConsoleProperties = consoleProperties;
  }

  @Override
  public void customizeCellRenderer(final @NotNull JTree tree,
                                    final Object value,
                                    final boolean selected,
                                    final boolean expanded,
                                    final boolean leaf,
                                    final int row,
                                    final boolean hasFocus) {
    myDurationText = null;
    myDurationColor = null;
    myDurationWidth = 0;
    myDurationLeftInset = 0;
    myDurationRightInset = 0;
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
    final Object userObj = node.getUserObject();
    if (userObj instanceof SMTRunnerNodeDescriptor desc) {
      final SMTestProxy testProxy = desc.getElement();

      if (testProxy instanceof SMTestProxy.SMRootTestProxy rootTestProxy) {
        if (rootTestProxy.isLeaf()) {
          TestsPresentationUtil.formatRootNodeWithoutChildren(rootTestProxy, this);
        } else {
          TestsPresentationUtil.formatRootNodeWithChildren(rootTestProxy, this);
        }
        if (myAdditionalRootFormatter != null) {
          myAdditionalRootFormatter.format(rootTestProxy, this);
        }
      } else {
        TestsPresentationUtil.formatTestProxy(testProxy, this);
      }

      if (TestConsoleProperties.SHOW_INLINE_STATISTICS.value(myConsoleProperties)) {
        myDurationText = getDurationText(testProxy, myConsoleProperties);
        if (myDurationText != null) {
          FontMetrics metrics = getFontMetrics(RelativeFont.SMALL.derive(getFont()));
          myDurationWidth = metrics.stringWidth(myDurationText);
          myDurationLeftInset = metrics.getHeight() / 4;
          myDurationRightInset = ExperimentalUI.isNewUI() ? tree.getInsets().right + JBUI.scale(4) : myDurationLeftInset;
          myDurationColor = selected ? UIUtil.getTreeSelectionForeground(hasFocus) : SimpleTextAttributes.GRAYED_ATTRIBUTES.getFgColor();
        }
      }

      return;
    }

    //strange node
    final @NlsSafe String text = node.toString();
    //no icon
    append(text != null ? text : SPACE_STRING, SimpleTextAttributes.GRAYED_ATTRIBUTES);
  }

  @Nls
  @ApiStatus.Experimental
  @ApiStatus.Internal
  public @Nullable String getDurationText(@NotNull SMTestProxy testProxy, @NotNull TestConsoleProperties consoleProperties) {
    return testProxy.getDurationString(myConsoleProperties);
  }

  @Override
  public @NotNull Dimension getPreferredSize() {
    Dimension preferredSize = super.getPreferredSize();
    if (myDurationWidth > 0) {
      preferredSize.width += myDurationWidth + myDurationLeftInset + myDurationRightInset;
    }
    return preferredSize;
  }

  public TestConsoleProperties getConsoleProperties() {
    return myConsoleProperties;
  }

  public void setAdditionalRootFormatter(@NotNull SMRootTestProxyFormatter formatter) {
    myAdditionalRootFormatter = formatter;
  }

  public void removeAdditionalRootFormatter() {
    myAdditionalRootFormatter = null;
  }

  @Override
  protected void paintComponent(Graphics g) {
    UISettings.setupAntialiasing(g);
    Shape clip = null;
    int width = getWidth();
    int height = getHeight();
    if (isOpaque()) {
      // paint background for expanded row
      g.setColor(getBackground());
      g.fillRect(0, 0, width, height);
    }
    if (myDurationWidth > 0) {
      width -= myDurationWidth + myDurationLeftInset + myDurationRightInset;
      if (width > 0 && height > 0) {
        g.setColor(myDurationColor);
        g.setFont(RelativeFont.SMALL.derive(getFont()));
        g.drawString(myDurationText, width + myDurationLeftInset, getTextBaseLine(g.getFontMetrics(), height));
        clip = g.getClip();
        g.clipRect(0, 0, width, height);
      }
    }
    super.paintComponent(g);
    // restore clip area if needed
    if (clip != null) g.setClip(clip);
  }

  @ApiStatus.Experimental
  public @Nullable @NlsSafe String getAccessibleStatus() {
    if (myAccessibleStatus == null) return null;
    return myAccessibleStatus.get();
  }

  @ApiStatus.Experimental
  public void setAccessibleStatus(@Nullable Computable<String> accessibleStatus) {
    myAccessibleStatus = accessibleStatus;
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleColoredTreeCellRendererWithContextMenu(new AccessibleColoredTreeCellRenderer() {
        @Override
        protected String getOriginalAccessibleName() {
          return getAccessibleNameWithoutIconTooltip();
        }
      });
    }
    return accessibleContext;
  }
}
