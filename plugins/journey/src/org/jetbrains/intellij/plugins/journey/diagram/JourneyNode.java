package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.DiagramNodeBase;
import com.intellij.diagram.DiagramProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.plugins.journey.util.PsiUtil;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.editor.ScrollType.CENTER_UP;

@SuppressWarnings("HardCodedStringLiteral")
public class JourneyNode extends DiagramNodeBase<JourneyNodeIdentity> {
  private Editor editor;
  private final List<PsiElement> elements = new ArrayList<>();
  private boolean foldingState = false;
  @NotNull private final JourneyNodeIdentity identity;
  @Nullable private final String myTitle;

  public JourneyNode(
    @NotNull DiagramProvider<JourneyNodeIdentity> provider,
    @NotNull JourneyNodeIdentity identity,
    @Nullable String title
  ) {
    super(provider);
    this.identity = identity;
    myTitle = title;
  }

  @Override
  public @NotNull JourneyNodeIdentity getIdentifyingElement() {
    return identity;
  }

  @Override
  public @Nullable @Nls String getTooltip() {
    return "Journey node tooltip " + identity.calculatePsiElement().getText();
  }

  @Override
  public @Nullable Icon getIcon() {
    return AllIcons.Process.ProgressResume; // TODO
  }

  @Override
  public @Nullable SimpleColoredText getPresentableTitle() {
    if (myTitle == null) return null;
    return new SimpleColoredText(myTitle, SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;

    final var that = (JourneyNode)obj;
    PsiElement psi1 = this.identity.calculatePsiElement();
    PsiElement psi2 = that.identity.calculatePsiElement();

    if (psi1.equals(psi2)) {
      return true;
    }

    return super.equals(obj);
  }

  public void addElement(PsiElement element) {
    if (editor == null) {
      return;
    }

    if (!elements.contains(element)) {
      elements.add(element);
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

  private List<TextRange> getRanges() {
    var members = ContainerUtil.map(elements, e -> PsiUtil.tryFindParentOrNull(e, it -> it instanceof PsiMember));
    var ranges = new ArrayList<TextRange>();
    if (!members.isEmpty()) {
      ranges.add(members.get(0).getTextRange());
      members.forEach(member -> {
        if (!ContainerUtil.exists(ranges, it -> it.contains(member.getTextRange()))) {
          ranges.removeIf(it -> member.getTextRange().contains(it));
          ranges.add(member.getTextRange());
        }
      });
    }
    ranges.sort(Comparator.comparing(e -> e.getStartOffset()));
    return ranges;
  }

  @RequiresEdt
  public void foldByMembers() {
    final FoldingModel foldingModel = editor.getFoldingModel();
    final Document document = editor.getDocument();

    foldingModel.runBatchFoldingOperation(() -> {
      // Iterate over all fold regions and remove them
      for (FoldRegion foldRegion : foldingModel.getAllFoldRegions()) {
          foldingModel.removeFoldRegion(foldRegion);
      }
    });

    List<TextRange> ranges = new ArrayList<>(ContainerUtil.map(getRanges(), e -> {
      return new TextRange(document.getLineStartOffset(getLineNumber(document, e.getStartOffset())), e.getEndOffset());
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
