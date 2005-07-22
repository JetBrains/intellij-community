package com.intellij.cvsSupport2.cvsBrowser;

import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.IdeaUIManager;

import javax.swing.*;

public class Cvs2Renderer extends ColoredTreeCellRenderer {
  public void customizeCellRenderer(JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
    append(value.toString(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN,
                                                      IdeaUIManager.getTreeForegroung()));
    if (value instanceof CvsElement) {
      setIcon(((CvsElement)value).getIcon(expanded));
    } else if (value instanceof LoadingNode) {
      setIcon(((LoadingNode)value).getIcon());
    }
  }
}
