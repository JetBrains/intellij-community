package com.intellij.cvsSupport2.cvsoperations.cvsAdd.ui;

import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddedFileInfo;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddedFileInfo;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddedFileInfo;
import com.intellij.util.ui.FileLabel;

import javax.swing.*;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.io.File;

/**
 * author: lesya
 */
public class AddedFileCellRenderer extends FileLabel implements TreeCellRenderer{
  public Component getTreeCellRendererComponent(JTree tree,
                                                Object value,
                                                boolean selected,
                                                boolean expanded,
                                                boolean leaf,
                                                int row,
                                                boolean hasFocus) {
    if (!(value instanceof AddedFileInfo)) {
      setIcon(null);
      setText("");
    }
    else {
      AddedFileInfo treeNode = (AddedFileInfo)value;
      setShowIcon(false);
      File file = new File(treeNode.getPresentableText());
      setFile(file);
      setIcon(treeNode.getIcon(expanded));
      int prefWidth = getIconWidth() + getFontMetrics(getFont()).stringWidth(getFilePath(file));
      setPreferredSize(new Dimension(prefWidth,
                       getPreferredSize().height));


    }
    return this;
  }
}
