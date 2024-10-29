// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.execution;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.MultilineTreeCellRenderer;
import com.intellij.ui.SideBorder;
import icons.AntIcons;

import javax.swing.*;
import java.awt.*;

final class MessageTreeRenderer extends MultilineTreeCellRenderer {

  private boolean myUseAnsiColor = false;
  private Color myDefaultForeground;

  private MessageTreeRenderer() {
  }

  public void setUseAnsiColor(boolean useAnsiColor) {
    myUseAnsiColor = useAnsiColor;
  }

  public static JScrollPane install(JTree tree) {
    JScrollPane scrollPane = MultilineTreeCellRenderer.installRenderer(tree, new MessageTreeRenderer());
    scrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT));
    return scrollPane;
  }

  @Override
  protected void initComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    if(value instanceof MessageNode messageNode) {
      setText(messageNode.getText(), messageNode.getTypeString() + messageNode.getPositionString());
    }
    else {
      String[] text = new String[] {value.toString()};
      if(text[0] == null) {
        text[0] = "";
      }
      setText(text, null);
    }

    Icon icon = null;
    Color foreground = myDefaultForeground;
    if (foreground == null) {
      myDefaultForeground = foreground = getForeground();
    }
    if (value instanceof MessageNode node) {
      AntBuildMessageView.MessageType type = node.getType();
      if (type == AntBuildMessageView.MessageType.BUILD) {
        icon = AntIcons.Build;
      }
      else if (type == AntBuildMessageView.MessageType.TARGET) {
        icon = AllIcons.Nodes.Target;
      }
      else if (type == AntBuildMessageView.MessageType.TASK) {
        icon = AntIcons.AntTask;
      }
      else if (type == AntBuildMessageView.MessageType.MESSAGE) {
        if (node.getPriority() == AntBuildMessageView.PRIORITY_WARN) {
          icon = AllIcons.General.Warning;
          foreground = ConsoleViewContentType.LOG_WARNING_OUTPUT.getAttributes().getForegroundColor();
        }
        else if (node.getPriority() == AntBuildMessageView.PRIORITY_INFO) {
          icon = AllIcons.General.Information;
          foreground = ConsoleViewContentType.LOG_INFO_OUTPUT.getAttributes().getForegroundColor();
        }
        else if (node.getPriority() == AntBuildMessageView.PRIORITY_VERBOSE) {
          icon = AntIcons.LogVerbose;
          foreground = ConsoleViewContentType.LOG_VERBOSE_OUTPUT.getAttributes().getForegroundColor();
        }
        else {
          icon = AntIcons.LogDebug;
          foreground = ConsoleViewContentType.LOG_DEBUG_OUTPUT.getAttributes().getForegroundColor();
        }
      }
      else if (type == AntBuildMessageView.MessageType.ERROR) {
        icon = AllIcons.General.Error;
        foreground = ConsoleViewContentType.LOG_ERROR_OUTPUT.getAttributes().getForegroundColor();
      }
    }
    if (myUseAnsiColor) {
      setForeground(foreground);
    }
    setIcon(icon);
  }

}
