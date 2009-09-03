package com.intellij.cvsSupport2.cvsBrowser;

import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;

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
                                                      UIUtil.getTreeForeground()));
    if (value instanceof CvsElement) {
      final CvsElement element = (CvsElement)value;
      if (element.isLoading()) {
        append(" (Loading...)", new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN,
                                                      UIUtil.getInactiveTextColor()));
      }
      setIcon(element.getIcon(expanded));
    } else if (value instanceof LoadingNode) {
      setIcon(((LoadingNode)value).getIcon());
    }
  }
}
