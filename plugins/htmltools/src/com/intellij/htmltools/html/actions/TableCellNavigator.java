package com.intellij.htmltools.html.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.htmltools.html.actions.TableUtil.*;

public final class TableCellNavigator {
  public static final class Directions {
    public static int[] RIGHT = new int[]{0, 1};
    public static int[] LEFT = new int[]{0, -1};
    public static int[] UP = new int[]{-1, 0};
    public static int[] DOWN = new int[]{1, 0};
  }

  public static boolean isActionAvailable(final Editor editor, final PsiFile file) {
    return isInsideTag(editor, file, new String[] {TD, TH});
  }

  private static boolean isInsideTable(List<TableRow> table, int row, int column) {
    return row >= 0 && row < table.size() && column >= 0 && column < table.get(row).size();
  }

  public static void moveCaret(final @NotNull Project project, final @NotNull Editor editor, final PsiFile file, int[] direction) {
    PsiElement element = getCurrentPsiElement(editor, file);
    element = getParentWithName(element, new String[]{TD, TH});
    assert element != null;
    PsiElement root = getTablePsiElement(element);
    if (root != null) {
      Pair<List<TableRow>, TableCell> pair = getTableAndPosition(root, element);
      List<TableRow> table = pair.first;
      TableCell cell = pair.second;
      int x = cell.startColumn;
      int y = cell.startRow;

      int newX = x;
      int newY = y;
      do {
        x = newX;
        y = newY;
        if (table.get(newY).get(newX).tag != cell.tag) {
          break;
        }
        newY += direction[0];
        newX += direction[1];
      } while (isInsideTable(table, newY, newX));

      TableCell newCell = table.get(y).get(x);
      XmlTag tag = newCell.tag;
      moveCaretTo(editor, findClosingToken(tag));
    }
  }
}
