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
