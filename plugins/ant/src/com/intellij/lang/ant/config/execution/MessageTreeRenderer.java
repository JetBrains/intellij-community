package com.intellij.lang.ant.config.execution;

import com.intellij.ant.AntElementRole;
import com.intellij.openapi.util.IconLoader;
import com.intellij.rt.ant.execution.AntMain2;
import com.intellij.ui.MultilineTreeCellRenderer;
import com.intellij.util.Icons;

import javax.swing.*;

final class MessageTreeRenderer extends MultilineTreeCellRenderer {

  private static final Icon myBuildIcon = IconLoader.getIcon("/ant/build.png");
  private static final Icon myMessageIcon = IconLoader.getIcon("/ant/message.png");
  private static final Icon myWarningIcon = IconLoader.getIcon("/compiler/warning.png");
  private static final Icon myErrorIcon = IconLoader.getIcon("/compiler/error.png");

  private MessageTreeRenderer() {
  }

  public static JScrollPane install(JTree tree) {
    return MultilineTreeCellRenderer.installRenderer(tree, new MessageTreeRenderer());
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
        icon = Icons.ANT_TARGET_ICON;
      }
      else if (type == AntBuildMessageView.MessageType.TASK) {
        icon = AntElementRole.TASK_ICON;
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
