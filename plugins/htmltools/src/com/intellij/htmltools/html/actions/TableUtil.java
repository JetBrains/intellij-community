package com.intellij.htmltools.html.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class TableUtil {
  static final @NonNls String TD = "td";
  static final @NonNls String TH = "th";
  static final @NonNls String TR = "tr";
  static final @NonNls String TABLE = "table";
  static final @NonNls String COLSPAN = "colspan";
  static final @NonNls String ROWSPAN = "rowspan";
  static final @NonNls String THEAD = "thead";

  static @Nullable PsiElement getParentWithName(PsiElement element, String[] tagNames) {
    while (element != null) {
      if (element instanceof XmlTag) {
        if (tagNames != null) {
          final String name = StringUtil.toLowerCase(((XmlTag)element).getName());
          if (ArrayUtil.contains(name, tagNames)) {
              return element;
          }
        }
        else {
          return element;
        }
      }
      element = element.getParent();
    }
    return null;
  }

  static boolean isInsideTag(final Editor editor, final PsiFile file, String[] tagNames) {
    if (isHtmlTagContainingFile(editor, file)) {
      final int offset = editor.getCaretModel().getOffset();
      PsiElement element = getParentWithName(file.findElementAt(offset), tagNames);
      return element != null;
    }

    return false;
  }

  static int getColumnsNumber(XmlTag tag) {
    XmlAttribute[] attributes = tag.getAttributes();
    for (XmlAttribute attribute : attributes) {
      if (COLSPAN.equals(attribute.getLocalName())) {
        try {
          return Integer.parseInt(attribute.getValue());
        }
        catch (NumberFormatException e) {
          return 1;
        }
      }
    }
    return 1;
  }

  static int getRowsNumber(XmlTag tag) {
    XmlAttribute[] attributes = tag.getAttributes();
    for (XmlAttribute attribute : attributes) {
      if (ROWSPAN.equals(attribute.getLocalName())) {
        try {
          return Integer.parseInt(attribute.getValue());
        }
        catch (NumberFormatException e) {
          return 1;
        }
      }
    }
    return 1;
  }

  static void generateTableList(PsiElement element, boolean isInsideHeader, List<? super Pair<Boolean, List<Integer>>> list,
                                List<? super XmlTag> tags) {
    if (element instanceof XmlTag tag) {
      final String name = StringUtil.toLowerCase(tag.getLocalName());
      if (THEAD.equals(name)) {
        isInsideHeader = true;
      }
      if (TD.equals(name) || TH.equals(name) || TR.equals(name) || TABLE.equals(name)) {
        list.add(new Pair<>(isInsideHeader, null));
        tags.add(tag);
      }
    }
    for (PsiElement psiElement : element.getChildren()) {
      generateTableList(psiElement, isInsideHeader, list, tags);
    }
  }

  static Pair<List<Pair<Boolean, List<Integer>>>, List<XmlTag>> generateTableTree(PsiElement root) {
    List<Pair<Boolean, List<Integer>>> list = new ArrayList<>();
    List<XmlTag> tags = new ArrayList<>();
    generateTableList(root, false, list, tags);
    List<Pair<Boolean, List<Integer>>> tree = new ArrayList<>();
    int tr = 0;
    for (int i = 0; i < list.size(); i++) {
      tree.add(new Pair<>(list.get(i).first, new ArrayList<>()));
      final String name = StringUtil.toLowerCase(tags.get(i).getLocalName());
      if (TR.equals(name)) {
        tree.get(0).second.add(i);
        tr = i;
      } else if (TD.equals(name) || TH.equals(name)) {
        tree.get(tr).second.add(i);
      }
    }

    return Pair.create(tree, tags);
  }

  //Iterate through row
  private static Pair<Integer, TableCell> getTableCell(List<Pair<Boolean, List<Integer>>> tree,
                                                        List<XmlTag> tags,
                                                        int index,
                                                        XmlTag tag, int currentColumn,
                                                        int currentRow, TableRow current, TableRow previous) {
    int add = 0;
    TableCell answer = null;
    XmlTag element = tags.get(index);
    if (TD.equals(StringUtil.toLowerCase(element.getLocalName())) || TH.equals(StringUtil.toLowerCase(element.getLocalName()))) {
      int cols = getColumnsNumber(element);
      int rows = getRowsNumber(element);

      for (int i = currentColumn; i < previous.size() && previous.get(i).getRemainingRowsNumber(currentRow) > 0; i++) {
        current.add(previous.get(i));
        add++;
      }
      TableCell cell = new TableCell(element, currentRow, currentColumn + add, rows, cols);
      if (tag == element) {
        answer = cell;
      }
      for (int i = 0; i < cols; i++) {
        current.add(cell);
      }
      add += cols;
    }
    for (int child : tree.get(index).second) {
      Pair<Integer, TableCell> tmp = getTableCell(tree, tags, child, tag, currentColumn + add, currentRow, current, previous);
      if (tmp.second != null) {
        answer = tmp.second;
      }
      add += tmp.first.intValue();
    }

    return new Pair<>(add, answer);
  }


  //Iterate through list of rows
  private static Pair<TableCell, TableRow> getTableCell(List<Pair<Boolean, List<Integer>>> tree, List<XmlTag> tags, int index, XmlTag tag, TableRow lastLevel, List<TableRow> table) {
    TableCell answer = null;
    XmlTag element = tags.get(index);
    if (TR.equals(StringUtil.toLowerCase(element.getLocalName()))) {
      TableRow currentLevel = new TableRow(element, tree.get(index).first.booleanValue());
      Pair<Integer, TableCell> tmp = getTableCell(tree, tags, index, tag, 0, table.size(), currentLevel, lastLevel);
      table.add(currentLevel);
      if (tmp.second != null) {
        answer = tmp.second;
      }
      for (int i = currentLevel.size(); i < lastLevel.size(); i++) {
        currentLevel.add(lastLevel.get(i));
      }
      lastLevel = currentLevel;
    }
    for (int child : tree.get(index).second) {
      Pair <TableCell, TableRow> tmp = getTableCell(tree, tags, child, tag, lastLevel, table);
      if (tmp.first != null) {
        answer = tmp.first;
      }
      lastLevel = tmp.second;
    }
    return Pair.create(answer, lastLevel);
  }

  static Pair<List<TableRow>, TableCell> getTableAndPosition(PsiElement root, PsiElement element) {
    Pair<List<Pair<Boolean, List<Integer>>>, List<XmlTag>> pair = generateTableTree(root);
    List<Pair<Boolean, List<Integer>>> tree = pair.first;
    List<XmlTag> tags = pair.second;
    List<TableRow> table = new ArrayList<>();
    TableCell cell = getTableCell(tree, tags, 0, (XmlTag)element, new TableRow(null, false), table).first;
    return Pair.create(table, cell);
  }

  static @Nullable PsiElement getCurrentPsiElement(final Editor editor, final PsiFile file) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement psiElement = file.findElementAt(offset);
    return PsiTreeUtil.getParentOfType(psiElement, XmlTag.class);
  }

  static @Nullable PsiElement getTablePsiElement(PsiElement root) {
    while (root != null && !(root instanceof XmlTag && TABLE.equals(StringUtil.toLowerCase(((XmlTag)root).getName())))) {
      root = root.getParent();
    }
    return root;
  }

  static @Nullable PsiElement findClosingToken(XmlTag tag) {
    PsiElement[] children = tag.getChildren();
    for (int i = 0; i < children.length; i++) {
      PsiElement element = children[i];
      if (element instanceof XmlToken token) {
        IElementType type = token.getTokenType();
        if (type.equals(XmlTokenType.XML_TAG_END)) {
          return children[i + 1];
        }
      }
    }
    return null;
  }

  static void moveCaretTo(final @NotNull Editor editor, final PsiElement element) {
    editor.getCaretModel().moveToOffset(element.getTextOffset());
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
  }

  public static boolean isHtmlTagContainingFile(final Editor editor, final PsiFile file) {
    if (editor == null || !(file instanceof XmlFile)) {
      return false;
    }
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement element = file.findElementAt(offset);
    return HtmlUtil.isHtmlTagContainingFile(element);
  }

  static final class TableCell {
    final int startColumn;
    final int startRow;
    final int cols;
    final int rows;
    final XmlTag tag;

    TableCell(XmlTag tag, int startRow, int startColumn, int rows, int cols) {
      this.tag = tag;
      this.startColumn = startColumn;
      this.startRow = startRow;
      this.rows = rows;
      this.cols = cols;
    }

    public int getRemainingColumnsNumber(int currentColumn) {
      return cols + startColumn - currentColumn;
    }

    public int getRemainingRowsNumber(int currentRow) {
      return rows + startRow - currentRow;
    }
  }

  static final class TableRow {
    final XmlTag rowTag;
    final List<TableCell> list;
    final boolean insideHeader;

    TableRow(XmlTag rowTag, boolean insideHeader) {
      this.rowTag = rowTag;
      list = new ArrayList<>();
      this.insideHeader = insideHeader;
    }

    public void add(TableCell cell) {
      list.add(cell);
    }

    public TableCell get(int index) {
      return list.get(index);
    }

    public int size() {
      return list.size();
    }

    public boolean isEmpty() {
      return list.isEmpty();
    }
  }
}
