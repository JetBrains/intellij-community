// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.terminal.TerminalUtils;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.JBTreeTraverser;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.JTextComponent;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.Component;
import java.util.List;
import java.util.stream.IntStream;

/**
 * @author gregsh
 */
final class ScratchImplUtil {
  private ScratchImplUtil() {
  }

  static @Nullable VirtualFile findFileIgnoreExtension(@NotNull VirtualFile dir, @NotNull String fileNameAndExt) {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    String fileName = ScratchFileCreator.getNameWithoutExtension(fileNameAndExt, dir.isCaseSensitive(), fileTypeManager);
    for (VirtualFile child : dir.getChildren()) {
      if (child.isDirectory()) continue;
      String childName = ScratchFileCreator.getNameWithoutExtension(child, fileTypeManager);
      if (!StringUtil.equalsIgnoreCase(childName, fileName)) continue;
      return child;
    }
    return null;
  }

  static @Nullable TextExtractor getTextExtractor(@Nullable Component component) {
    return component instanceof JTextComponent ? new TextComponentExtractor((JTextComponent)component) :
           component instanceof JList ? new ListExtractor((JList<?>)component) :
           component instanceof JTree ? new TreeExtractor((JTree)component) :
           component instanceof JTable ? new TableExtractor((JTable)component) :
           TerminalUtils.isTerminalComponent(component) ? new TerminalExtractor(component) :
           null;
  }

  interface TextExtractor {
    boolean hasSelection();
    @Nullable String extractText();
  }

  private static final class TerminalExtractor implements TextExtractor {
    final Component component;

    TerminalExtractor(@Nullable Component dataContext) {
      component = dataContext;
    }

    @Override
    public boolean hasSelection() {
      return TerminalUtils.hasSelectionInTerminal(component);
    }

    @Override
    public String extractText() {
      if (TerminalUtils.hasSelectionInTerminal(component)) {
        return TerminalUtils.getSelectedTextInTerminal(component);
      }
      String text = TerminalUtils.getTextInTerminal(component);
      return StringUtil.isEmptyOrSpaces(text) ? null : text;
    }
  }

  private static final class TextComponentExtractor implements TextExtractor {
    final JTextComponent comp;

    TextComponentExtractor(@NotNull JTextComponent component) { comp = component; }

    @Override
    public boolean hasSelection() {
      return comp.getSelectionStart() != comp.getSelectionEnd();
    }

    @Override
    public String extractText() {
      String text = comp.getSelectedText();
      return StringUtil.isEmpty(text) ? comp.getText() : text;
    }
  }

  private static final class ListExtractor implements TextExtractor {
    final JList<Object> comp;

    /** @noinspection unchecked*/
    ListExtractor(@NotNull JList<?> component) { comp = (JList<Object>)component; }

    @Override
    public boolean hasSelection() {
      return comp.getSelectionModel().getSelectedItemsCount() > 1;
    }

    @Override
    public String extractText() {
      List<Object> selection = comp.getSelectedValuesList();
      JBIterable<Object> values;
      if (selection.size() > 1) {
        values = JBIterable.from(selection);
      }
      else {
        ListModel<Object> model = comp.getModel();
        values = JBIterable.generate(0, o -> o + 1).take(model.getSize()).map(o -> model.getElementAt(o));
      }
      ListCellRenderer<Object> renderer = comp.getCellRenderer();
      StringBuilder sb = new StringBuilder();
      for (Object value : values) {
        append(sb, value, renderer.getListCellRendererComponent(comp, value, -1, false, false));
        sb.append("\n");
      }
      return sb.toString();
    }
  }

  private static final class TreeExtractor implements TextExtractor {
    final JTree comp;

    TreeExtractor(@NotNull JTree component) { comp = component; }

    @Override
    public boolean hasSelection() {
      return comp.getSelectionModel().getSelectionCount() > 1;
    }

    @Override
    public String extractText() {
      TreePath[] selection = comp.getSelectionPaths();
      int initialDepth;
      JBIterable<TreePath> paths;
      JBTreeTraverser<TreePath> traverser = TreeUtil.treePathTraverser(comp).expand(comp::isExpanded);
      if (selection != null && selection.length > 1) {
        paths = traverser.withRoots(selection).unique().traverse();
        initialDepth = Integer.MAX_VALUE;
        for (TreePath path : selection) {
          initialDepth = Math.min(initialDepth, path.getPathCount() - 1);
        }
      }
      else {
        initialDepth = comp.isRootVisible() ? 0 : 1;
        paths = traverser.traverse().skip(initialDepth);
      }
      TreeCellRenderer renderer = comp.getCellRenderer();
      StringBuilder sb = new StringBuilder();
      for (TreePath path : paths) {
        Object value = path.getLastPathComponent();
        int depth = path.getPathCount() - initialDepth - 1;
        //noinspection StringRepeatCanBeUsed
        for (int i = 0; i < depth; i++) sb.append("  ");
        append(sb, value, renderer.getTreeCellRendererComponent(comp, value, false, false, false, -1, false));
        sb.append("\n");
      }
      return sb.toString();
    }
  }

  private static final class TableExtractor implements TextExtractor {
    final JTable comp;

    TableExtractor(@NotNull JTable component) { comp = component; }

    @Override
    public boolean hasSelection() {
      return comp.getSelectionModel().getSelectedItemsCount() > 1;
    }

    @Override
    public String extractText() {
      int[] rows = comp.getSelectedRows();
      int[] cols = comp.getColumnSelectionAllowed() ?
                   comp.getSelectedColumns() : IntStream.range(0, comp.getColumnCount()).toArray();
      StringBuilder sb = new StringBuilder();
      int lastCol = cols.length == 0 ? -1 : cols[cols.length - 1];
      for (int row : rows) {
        for (int col : cols) {
          Object value = comp.getModel().getValueAt(comp.convertRowIndexToModel(row), comp.convertColumnIndexToModel(col));
          TableCellRenderer renderer = comp.getCellRenderer(row, col);
          append(sb, value, renderer.getTableCellRendererComponent(comp, value, false, false, row, col));
          if (col != lastCol) sb.append("    ");
        }
        sb.append("\n");
      }
      return sb.toString();
    }
  }

  private static void append(StringBuilder sb, Object value, Component renderer) {
    int length = sb.length();
    if (renderer instanceof JPanel) {
      for (Component c : UIUtil.uiTraverser(renderer)) {
        if (appendSimple(sb, c)) sb.append(" ");
      }
      if (sb.length() == length) {
        sb.append(TreeUtil.getUserObject(value));
      }
    }
    else if (!appendSimple(sb, renderer)) {
      sb.append(TreeUtil.getUserObject(value));
    }
    // replace various space chars like `FontUtil#thinSpace` with just space
    for (int i = length, len = sb.length(); i < len; i++) {
      char c = sb.charAt(i);
      if (c >= '\u2000' && c <= '\u2009') sb.setCharAt(i, ' ');
    }
  }

  private static boolean appendSimple(@NotNull StringBuilder sb, @Nullable Component renderer) {
    if (renderer instanceof JLabel) {
      sb.append(((JLabel)renderer).getText());
    }
    else if (renderer instanceof JTextComponent) {
      sb.append(((JTextComponent)renderer).getText());
    }
    else if (renderer instanceof SimpleColoredComponent) {
      sb.append(((SimpleColoredComponent)renderer).getCharSequence(false));
    }
    else {
      return false;
    }
    return true;
  }
}
