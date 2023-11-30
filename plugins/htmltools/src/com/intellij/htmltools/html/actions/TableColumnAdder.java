package com.intellij.htmltools.html.actions;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.htmltools.html.actions.TableUtil.*;

public final class TableColumnAdder {
  public static boolean isActionAvailable(final Editor editor, final PsiFile file) {
    return isInsideTag(editor, file, new String[] {TD, TH});
  }

  private static void addCellToTag(@NonNls Project project, TableCell cell, boolean toPlaceBefore, boolean insideHeader) throws IncorrectOperationException {
    if (toPlaceBefore) {
      XmlTag newCell = createTableCellXmlTag(project, insideHeader);
      cell.tag.getParent().addBefore(newCell, cell.tag);
    } else {
      XmlTag newCell = createTableCellXmlTag(project, insideHeader);
      cell.tag.getParent().addAfter(newCell, cell.tag);
    }
  }

  private static void addColumnToTable(final @NotNull Project project, List<TableRow> table, int columnNumber) {
    for (int i = 0; i < table.size(); i++) {
      TableRow row = table.get(i);
      if (row.isEmpty()) {
        XmlTag element = createTableCellXmlTag(project, row.insideHeader);
        row.rowTag.add(element);
      }
      else if (columnNumber < 0) {
        TableCell cell = null;
        for (int j = 0; j < row.size(); j++) {
          TableCell currentCell = row.get(j);
          if (currentCell.startRow == i) {
            cell = currentCell;
            break;
          }
        }
        if (cell != null) {
          addCellToTag(project, cell, true, row.insideHeader);
        }
      }
      else {
        loop:
        for (int j = Math.min(columnNumber, row.size() - 1); j >= 0; j--) {
          TableCell cell = row.get(j);
          for (XmlAttribute attribute : cell.tag.getAttributes()) {
            if (COLSPAN.equals(StringUtil.toLowerCase(attribute.getLocalName()))) {
              if (cell.startRow == i) {
                try {
                  cell.tag.setAttribute(COLSPAN, String.valueOf((Integer.parseInt(cell.tag.getAttributeValue(COLSPAN)) + 1)));
                }
                catch (NumberFormatException e) {
                  break;
                }
              }
              break loop;
            }
          }
          if (cell.startRow == i) {
            addCellToTag(project, cell, false, row.insideHeader);
            break;
          }
        }
      }
    }
  }

  @SuppressWarnings("ALL")
  private static XmlTag createTableCellXmlTag(@NotNull final Project project, boolean inHeader) {
    final String filetext = "<root>" + (inHeader ? "<th></th>" : "<td></td>") + "</root";
    final XmlFile xmlFile = (XmlFile)PsiFileFactory.getInstance(project).createFileFromText("dummy.xml", XMLLanguage.INSTANCE,
                                                                                            filetext);
    XmlTag tag = xmlFile.getDocument().getRootTag();
    for (PsiElement element : tag.getChildren()) {
      if (element instanceof XmlTag) {
        tag = (XmlTag)element;
      }
    }
    return tag;
  }

  private static boolean moveCaretToFirstEmptyCell(@NotNull Editor editor, PsiElement element) {
    if (element instanceof XmlTag && (TD.equals(StringUtil.toLowerCase(((XmlTag)element).getName())) || TH.equals(
      StringUtil.toLowerCase(((XmlTag)element).getName())))) {
      if ("<td></td>".equals(element.getText()) || "<th></th>".equals(element.getText())) {
        PsiElement closingToken = element.getChildren()[3]; // "</" position
        moveCaretTo(editor, closingToken);
        return true;
      }
    }
    for (PsiElement child : element.getChildren()) {
      if(moveCaretToFirstEmptyCell(editor, child)) {
        return true;
      }
    }
    return false;
  }

  public static void addColumn(final @NotNull Project project, final Editor editor, final PsiFile file, boolean toInsertBefore)
    throws IncorrectOperationException {
    PsiElement element = getCurrentPsiElement(editor, file);
    element = getParentWithName(element, new String[]{TD, TH});
    assert element != null;

    PsiElement root = getTablePsiElement(element);
    Pair<List<TableRow>, TableCell> pair = getTableAndPosition(root, element);
    List<TableRow> table = pair.first;
    int columnNumber = pair.second.startColumn;
    if (!toInsertBefore) {
      columnNumber += getColumnsNumber((XmlTag)element);
    }
    addColumnToTable(project, table, columnNumber - 1);

    moveCaretToFirstEmptyCell(editor, root);
  }
}
