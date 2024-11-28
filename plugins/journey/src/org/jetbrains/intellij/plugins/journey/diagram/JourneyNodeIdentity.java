package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.LazyPsiElementHolder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.intellij.plugins.journey.util.PsiUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.intellij.openapi.editor.ScrollType.CENTER_UP;

public class JourneyNodeIdentity implements LazyPsiElementHolder {
  private Editor editor;
  private final List<PsiMember> elements = new ArrayList<>();
  private final PsiFile file;
  private boolean foldingState = false;

  JourneyNodeIdentity(@NotNull PsiElement element) {
    PsiMember psiElement = (PsiMember)PsiUtil.tryFindParentOrNull(element, it -> it instanceof PsiMember);
    elements.add(psiElement);
    file = ReadAction.nonBlocking(() -> element.getContainingFile()).executeSynchronously();
  }

  public PsiMember getOriginalElement() {
    return elements.get(0);
  }

  @Override
  public @NotNull PsiFile calculatePsiElement() {
    return file;
  }

  public void addElement(PsiMember element) {
    if (!ContainerUtil.exists(elements, it -> it.getTextRange().contains(element.getTextRange()))) {
      elements.removeIf(it -> element.getTextRange().contains(it.getTextRange()));
      elements.add(element);
      elements.sort(Comparator.comparing(e -> e.getTextRange().getStartOffset()));
    }
    ApplicationManager.getApplication().invokeLater(() -> {
      foldByMembers();
      editor.getCaretModel().moveToOffset(element.getTextRange().getStartOffset());
      editor.getScrollingModel().scrollToCaret(CENTER_UP);
      editor.getComponent().revalidate();
    });
  }

  public void setEditor(Editor editor) {
    this.editor = editor;
  }

  private static int getLineNumber(Document document, int offset) {
    return document.getLineNumber(offset);
  }

  public void setFoldingState(boolean foldingState) {
    this.foldingState = foldingState;
    foldByMembers();
  }

  @RequiresEdt
  public void foldByMembers() {
    final FoldingModel foldingModel = editor.getFoldingModel();
    final Document document = editor.getDocument();
    List<TextRange> ranges = new ArrayList<>(ContainerUtil.map(elements, e -> {
      TextRange range = e.getTextRange();
      return new TextRange(document.getLineStartOffset(getLineNumber(document, range.getStartOffset())), range.getEndOffset());
    }));
    final TextRange endTile = new TextRange(
      document.getLineEndOffset(getLineNumber(document, document.getTextLength() - 1)), document.getTextLength());
    ranges.add(endTile);

    int nextFoldingLine = 0;
    for (TextRange range : ranges) {
      final boolean isLast = range.equals(endTile);
      final int endFolding = range.getStartOffset();
      final int startFolding = document.getLineStartOffset(Math.min(document.getLineCount() - 1, nextFoldingLine));
      // Run folding operations within a batch to ensure atomic updates
      foldingModel.runBatchFoldingOperation(() -> {
        // Create a fold region before the visible range
        if (endFolding > 0) {
          FoldRegion region = foldingModel.getFoldRegion(startFolding, endFolding);
          if (region == null) {
            region = foldingModel.addFoldRegion(startFolding, endFolding, "");
          }
          if (region != null) {
            region.setExpanded(foldingState);
          }
        }
      });
      if (!isLast) {
        nextFoldingLine = getLineNumber(document, range.getEndOffset()) + 1;
        if (nextFoldingLine >= document.getLineCount()) {
          break;
        }
        String nextLineText = document.getText(
          new TextRange(document.getLineStartOffset(nextFoldingLine), document.getLineEndOffset(nextFoldingLine)));
        if (nextLineText.replaceAll("\\s+", "").isEmpty()) {
          nextFoldingLine++;
        }
      }
    }
  }
}
