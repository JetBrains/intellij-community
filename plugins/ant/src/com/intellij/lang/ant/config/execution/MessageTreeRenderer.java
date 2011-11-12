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

import com.intellij.lang.ant.AntIcons;
import com.intellij.openapi.util.IconLoader;
import com.intellij.rt.ant.execution.AntMain2;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.MultilineTreeCellRenderer;
import com.intellij.ui.SideBorder;
import com.intellij.util.PlatformIcons;

import javax.swing.*;

final class MessageTreeRenderer extends MultilineTreeCellRenderer {

  private static final Icon myBuildIcon = IconLoader.getIcon("/ant/build.png");
  private static final Icon myMessageIcon = IconLoader.getIcon("/ant/message.png");
  private static final Icon myWarningIcon = IconLoader.getIcon("/compiler/warning.png");
  private static final Icon myErrorIcon = IconLoader.getIcon("/compiler/error.png");

  private MessageTreeRenderer() {
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

    if (value instanceof MessageNode) {
      MessageNode node = (MessageNode)value;
      AntBuildMessageView.MessageType type = node.getType();
      if (type == AntBuildMessageView.MessageType.BUILD) {
        icon = myBuildIcon;
      }
      else if (type == AntBuildMessageView.MessageType.TARGET) {
        icon = AntIcons.ANT_TARGET_ICON;
      }
      else if (type == AntBuildMessageView.MessageType.TASK) {
        icon = PlatformIcons.TASK_ICON;
      }
      else if (type == AntBuildMessageView.MessageType.MESSAGE) {
        if (node.getPriority() == AntMain2.MSG_WARN) {
          icon = myWarningIcon;
        }
        else {
          icon = myMessageIcon;
        }
      }
      else if (type == AntBuildMessageView.MessageType.ERROR) {
        icon = myErrorIcon;
      }
    }
    setIcon(icon);
  }
}
