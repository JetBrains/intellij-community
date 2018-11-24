/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.ant.config.execution;

import com.intellij.execution.process.ConsoleHighlighter;
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

  protected void initComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    if(value instanceof MessageNode) {
      MessageNode messageNode = (MessageNode)value;
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
    if (value instanceof MessageNode) {
      MessageNode node = (MessageNode)value;
      AntBuildMessageView.MessageType type = node.getType();
      if (type == AntBuildMessageView.MessageType.BUILD) {
        icon = AntIcons.Build;
      }
      else if (type == AntBuildMessageView.MessageType.TARGET) {
        icon = AntIcons.Target;
      }
      else if (type == AntBuildMessageView.MessageType.TASK) {
        icon = AntIcons.Task;
      }
      else if (type == AntBuildMessageView.MessageType.MESSAGE) {
        if (node.getPriority() == AntBuildMessageView.PRIORITY_WARN) {
          icon = AllIcons.General.Warning;
          foreground = ConsoleHighlighter.DARKGRAY.getDefaultAttributes().getForegroundColor();
        }
        else if (node.getPriority() == AntBuildMessageView.PRIORITY_BRIEF) {
          icon = AntIcons.Message;
          foreground = ConsoleHighlighter.BLUE.getDefaultAttributes().getForegroundColor();
        }
        else {
          icon = AntIcons.Message;
          foreground = ConsoleHighlighter.GREEN.getDefaultAttributes().getForegroundColor();
        }
      }
      else if (type == AntBuildMessageView.MessageType.ERROR) {
        icon = AllIcons.General.Error;
        foreground = ConsoleHighlighter.RED.getDefaultAttributes().getForegroundColor();
      }
    }
    if (myUseAnsiColor) {
      setForeground(foreground);
    }
    setIcon(icon);
  }

}
