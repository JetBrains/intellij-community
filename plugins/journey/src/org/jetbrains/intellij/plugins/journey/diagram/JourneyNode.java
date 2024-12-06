package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.DiagramNodeBase;
import com.intellij.diagram.DiagramProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.plugins.journey.util.PsiUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.List;

import static com.intellij.codeInsight.highlighting.BraceHighlightingHandler.LAYER;
import static com.intellij.openapi.editor.markup.EffectType.LINE_UNDERSCORE;
import static com.intellij.openapi.editor.markup.HighlighterTargetArea.EXACT_RANGE;
import static org.jetbrains.intellij.plugins.journey.diagram.JourneyDiagramLayout.getRealizer;

@SuppressWarnings("HardCodedStringLiteral")
public class JourneyNode extends DiagramNodeBase<JourneyNodeIdentity> {
  public Editor editor;
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
    return "Journey node tooltip " + Objects.requireNonNull(identity.getIdentifierElement().getElement()).getText();
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

  public void setEditor(Editor editor) {
    this.editor = editor;
  }

  private static int getLineNumber(Document document, int offset) {
    return document.getLineNumber(offset);
  }

  public void setFullViewState(boolean fullViewState, JourneyDiagramDataModel dataModel) {
    this.fullViewState = fullViewState;
    ApplicationManager.getApplication().invokeLater(() -> {
      dataModel.highlightNode(this);
    });
  }

  private static List<TextRange> getRanges(List<SmartPsiElementPointer> psiElements) {
    var members = ContainerUtil.map(psiElements, e -> PsiUtil.tryFindParentOrNull(e.getElement(),
                                                                                  it -> it instanceof PsiMember));
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

  public void highlightNode(List<SmartPsiElementPointer> psiElements) {
    if (editor == null) {
      return;
    }

    // Define the text attributes for highlighting
    TextAttributes highlighting = new TextAttributes();
//    highlighting.setBackgroundColor(new Color(243, 255, 223));
    highlighting.setBackgroundColor(new Color(93, 93, 93));
    highlighting.setEffectType(null);
    MarkupModel markupModel = this.editor.getMarkupModel();

    psiElements.forEach(it -> {
      if (it instanceof PsiMethod psiMethod) {
        markupModel.addRangeHighlighter(Objects.requireNonNull(psiMethod.getNameIdentifier()).getTextOffset(),
            psiMethod.getParameterList().getTextRange().getEndOffset(), HighlighterLayer.ADDITIONAL_SYNTAX, highlighting, EXACT_RANGE);
      } else {
        markupModel.addRangeHighlighter(Objects.requireNonNull(it.getPsiRange()).getStartOffset(),
            Objects.requireNonNull(it.getPsiRange()).getEndOffset(), HighlighterLayer.ADDITIONAL_SYNTAX, highlighting, EXACT_RANGE);
      }
    });
  }

  public void highlightMembers(List<SmartPsiElementPointer> psiElements) {
    if (editor == null) {
      return;
    }

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

    List<TextRange> ranges = new ArrayList<>(ContainerUtil.map(getRanges(psiElements), e -> {
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
      if (startFolding <= endFolding) {
        markupModel.addRangeHighlighter(startFolding, endFolding, LAYER, attributes, EXACT_RANGE);
      }
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
    highlightNode(psiElements);
  }

  private static Point2D.Double getLinePositionWithScroll(Editor editor, int offset) {
    // Convert offset to XY position relative to the entire document
    Point absolutePosition = editor.offsetToXY(offset);

    // Retrieve the current scrolling offsets
    ScrollingModel scrollingModel = editor.getScrollingModel();
    Rectangle visibleArea = scrollingModel.getVisibleArea();

    int scrollX = scrollingModel.getHorizontalScrollOffset();
    int scrollY = scrollingModel.getVerticalScrollOffset();

    // Calculate relative position considering the scrolling offset
    int relativeX = absolutePosition.x - scrollX;
    int relativeY = absolutePosition.y - scrollY;

    return new Point2D.Double((double)relativeX / visibleArea.width, ((double)relativeY / visibleArea.height));
  }

  public double getRealizerCoord(SmartPsiElementPointer element) {
    if (editor == null) {
      return 0.0;
    }

    int lineStartOffset = editor.getDocument().getLineStartOffset(editor.getDocument().getLineNumber(
      Objects.requireNonNull(element.getElement()).getTextRange().getStartOffset()));
    Point2D.Double p1 = getLinePositionWithScroll(editor, lineStartOffset);
    final double OFFSET = 25.0;
    double height = getRealizer(Objects.requireNonNull(getBuilder()), this).get().getHeight() - OFFSET;
    double newY = -height / 2.0 + p1.y * height + OFFSET;
    newY = Math.max(-height / 2.0 + OFFSET, newY);
    newY = Math.min(height / 2.0, newY);
    return newY;
  }
}
