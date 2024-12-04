package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.DiagramNodeBase;
import com.intellij.diagram.DiagramProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.plugins.journey.util.PsiUtil;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

import static com.intellij.codeInsight.highlighting.BraceHighlightingHandler.LAYER;
import static com.intellij.openapi.editor.markup.EffectType.LINE_UNDERSCORE;
import static com.intellij.openapi.editor.markup.HighlighterTargetArea.EXACT_RANGE;

@SuppressWarnings("HardCodedStringLiteral")
public class JourneyNode extends DiagramNodeBase<JourneyNodeIdentity> {
  private Editor editor;
  private final List<PsiElement> elements = new ArrayList<>();
  private boolean fullViewState = false;
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
    return "Journey node tooltip " + identity.getOriginalElement().getText();
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

    if (this.identity.equals(that.identity)) {
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
  }

  public void setEditor(Editor editor) {
    this.editor = editor;
  }

  private static int getLineNumber(Document document, int offset) {
    return document.getLineNumber(offset);
  }

  public void setFullViewState(boolean fullViewState) {
    this.fullViewState = fullViewState;
    ApplicationManager.getApplication().invokeLater(() -> {
      highlightMembers();
    });
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

  boolean isFullViewState() {
    return fullViewState;
  }

  public void highlightNode(PsiMember element, List<JourneyEdge> myEdges) {
    if (editor == null) {
      return;
    }

    // Define the text attributes for highlighting
    TextAttributes secondLevelHighlight = new TextAttributes();
    secondLevelHighlight.setBackgroundColor(new Color(243, 255, 243));
    secondLevelHighlight.setEffectType(null);

    TextAttributes firstLevelHighlight = new TextAttributes();
    firstLevelHighlight.setBackgroundColor(new Color(255, 255, 243));
    firstLevelHighlight.setEffectType(null);

    var incomeNodes = myEdges.stream().filter(it -> it.getTarget().equals(this)).map(e -> e.getSource()).toList();
    var outcomeNodes = myEdges.stream().filter(it -> it.getSource().equals(this)).map(e -> e.getTarget()).toList();

    Set allNodes = new HashSet();
    allNodes.addAll(incomeNodes);
    allNodes.addAll(outcomeNodes);
    allNodes.add(this);
    allNodes.forEach(it -> ((JourneyNode)(it)).highlightMembers());

    var internalElements = elements.stream().filter(it -> element.getTextRange().contains(it.getTextRange())).toList();

    this.editor.getMarkupModel().addRangeHighlighter(element.getTextRange().getStartOffset(), element.getTextRange().getEndOffset(),
                                                     HighlighterLayer.SELECTION, firstLevelHighlight, EXACT_RANGE);

    internalElements.forEach(usage -> {
      if (usage instanceof PsiReferenceExpressionImpl referenceExpression) {
        outcomeNodes.forEach(it -> {
          JourneyNode journeyNode = (JourneyNode)it;
          Optional<PsiElement> method = journeyNode.elements.stream().filter(e -> e.equals(referenceExpression.resolve())).findFirst();
          if (method.isPresent()) {
            MarkupModel markupModel = this.editor.getMarkupModel();
            markupModel.addRangeHighlighter(usage.getTextRange().getStartOffset(), usage.getTextRange().getEndOffset(),
                                            HighlighterLayer.SELECTION, firstLevelHighlight, EXACT_RANGE);
            markupModel = journeyNode.editor.getMarkupModel();
            markupModel.addRangeHighlighter(method.get().getTextRange().getStartOffset(), method.get().getTextRange().getEndOffset(),
                                            HighlighterLayer.SELECTION, firstLevelHighlight, EXACT_RANGE);
          }
        });
      }
    });

    incomeNodes.forEach(it -> {
      JourneyNode journeyNode = (JourneyNode)it;
      journeyNode.elements.forEach(usage -> {
        if (usage instanceof PsiReferenceExpressionImpl referenceExpression) {
          if (Objects.equals(referenceExpression.resolve(), element)) {
            MarkupModel markupModel = journeyNode.editor.getMarkupModel();
            markupModel.addRangeHighlighter(usage.getTextRange().getStartOffset(), usage.getTextRange().getEndOffset(),
                                            HighlighterLayer.SELECTION, secondLevelHighlight, EXACT_RANGE);
          }
        }
      });
    });
  }

  @RequiresEdt
  public void highlightMembers() {
    final FoldingModel foldingModel = editor.getFoldingModel();
    final Document document = editor.getDocument();
    MarkupModel markupModel = editor.getMarkupModel();
    markupModel.removeAllHighlighters();

    if (isFullViewState()) {
      return;
    }

    EditorColorsScheme scheme = ObjectUtils.notNull(editor.getColorsScheme(), EditorColorsManager.getInstance().getGlobalScheme());
    Color background = scheme.getDefaultBackground();
    //noinspection UseJBColor
    Color foreground = Registry.getColor(ColorUtil.isDark(background) ?
                                         "editor.focus.mode.color.dark" :
                                         "editor.focus.mode.color.light", Color.GRAY);
    TextAttributes attributes = new TextAttributes(foreground, background, background, LINE_UNDERSCORE, Font.PLAIN);

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
      markupModel.addRangeHighlighter(startFolding, endFolding, LAYER, attributes, EXACT_RANGE);
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
