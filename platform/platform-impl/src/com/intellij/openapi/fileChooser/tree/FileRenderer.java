/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.fileChooser.tree;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import javax.swing.Icon;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.JTree;

import static com.intellij.openapi.fileChooser.FileElement.isFileHidden;
import static com.intellij.openapi.util.IconLoader.getTransparentIcon;

/**
 * @author Sergey.Malenkov
 */
public class FileRenderer {
  private static final Color GRAYED = SimpleTextAttributes.GRAYED_ATTRIBUTES.getFgColor();
  private static final Color HIDDEN = SimpleTextAttributes.DARK_TEXT.getFgColor();

  public <T> ColoredListCellRenderer<T> forList() {
    return new ColoredListCellRenderer<T>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends T> list, T value, int index, boolean selected, boolean focused) {
        customize(this, value, selected, focused);
      }
    };
  }

  public ColoredTableCellRenderer forTable() {
    return new ColoredTableCellRenderer() {
      @Override
      protected void customizeCellRenderer(JTable table, @Nullable Object value, boolean selected, boolean focused, int row, int column) {
        customize(this, value, selected, focused);
      }
    };
  }

  public ColoredTreeCellRenderer forTree() {
    return new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull JTree tree, Object value,
                                        boolean selected, boolean expanded, boolean leaf, int row, boolean focused) {
        customize(this, value, selected, focused);
      }
    };
  }

  protected void customize(SimpleColoredComponent renderer, Object value, boolean selected, boolean focused) {
    int style = SimpleTextAttributes.STYLE_PLAIN;
    Color color = null;
    Icon icon = null;
    String name = null;
    String comment = null;
    boolean hidden = false;
    boolean valid = true;
    if (value instanceof FileNode) {
      FileNode node = (FileNode)value;
      icon = node.getIcon();
      name = node.getName();
      comment = node.getComment();
      hidden = node.isHidden();
      valid = node.isValid();
    }
    else if (value instanceof VirtualFile) {
      VirtualFile file = (VirtualFile)value;
      name = file.getName();
      hidden = isFileHidden(file);
      valid = file.isValid();
    }
    else if (value != null) {
      name = value.toString();
      color = GRAYED;
    }
    if (!valid) style |= SimpleTextAttributes.STYLE_STRIKEOUT;
    if (hidden) color = HIDDEN;
    renderer.setIcon(!hidden || icon == null ? icon : getTransparentIcon(icon));
    SimpleTextAttributes attributes = new SimpleTextAttributes(style, color);
    if (name != null) renderer.append(name, attributes);
    if (comment != null) renderer.append(comment, attributes);
  }
}
