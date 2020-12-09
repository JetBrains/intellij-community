// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.inline;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.openapi.editor.impl.InlayModelImpl;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.DocumentUtil;
import com.intellij.util.MathUtil;
import com.intellij.util.Producer;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XNamedValue;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.evaluate.XDebuggerEditorLinePainter;
import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerTreeCreator;
import com.intellij.xdebugger.impl.frame.XWatchesView;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;

public final class XDebuggerInlayUtil {
  public static final Key<Helper> HELPER_KEY = Key.create("xdebug.inlay.helper");
  public static final String INLINE_HINTS_DELIMETER = ":";

  private static int getIdentifierEndOffset(@NotNull CharSequence text, int startOffset) {
    while (startOffset < text.length() && Character.isJavaIdentifierPart(text.charAt(startOffset))) startOffset++;
    return startOffset;
  }

  public static boolean createLineEndInlay(XValueNodeImpl valueNode,
                                           @NotNull XDebugSession session,
                                           @NotNull VirtualFile file,
                                           @NotNull XSourcePosition position,
                                           Document document) {
    XValue container = valueNode.getValueContainer();
    SimpleColoredText valuePresentation = XDebuggerEditorLinePainter.createPresentation(valueNode);
    if (valuePresentation != null) {
      UIUtil.invokeLaterIfNeeded(() -> {

        TextAttributes attributes = XDebuggerEditorLinePainter.getAttributes(position.getLine(), position.getFile(), session);

        SimpleColoredText variablePresentation = XDebuggerEditorLinePainter
          .computeVariablePresentationWithChanges(valueNode, valueNode.getName(), valuePresentation, attributes, position.getLine(), session.getProject());


        int offset = document.getLineEndOffset(position.getLine());
        Project project = session.getProject();

        FileEditor editor = FileEditorManager.getInstance(project).getSelectedEditor(file);
        if (editor instanceof TextEditor) {
          Editor e = ((TextEditor)editor).getEditor();

          XDebuggerTreeCreator creator =
            new XDebuggerTreeCreator(project, session.getDebugProcess().getEditorsProvider(), session.getCurrentPosition(), ((XDebugSessionImpl)session).getValueMarkers());

          Consumer<Inlay> onClick = (inlay) -> {
            InlineDebugRenderer inlayRenderer = (InlineDebugRenderer)inlay.getRenderer();
            if (inlayRenderer.myPopupIsShown) {
              return;
            }
            String name = "valueName";
            if (container instanceof XNamedValue) {
              name = ((XNamedValue)container).getName();
            }
            Pair<XValue, String> descriptor = Pair.create(container, name);
            Rectangle bounds = inlay.getBounds();
            Point point = new Point(bounds.x, bounds.y + bounds.height);

            inlayRenderer.myPopupIsShown = true;
            XDebuggerTreeInlayPopup.showTreePopup(creator, descriptor, valueNode, e, point, position, session, () -> {
              ApplicationManager.getApplication().invokeLater(() -> { inlayRenderer.myPopupIsShown = false; });
            });
          };

          boolean customNode = valueNode instanceof InlineWatchNodeImpl;
          XWatchesView view = null;
          if (customNode) {
            XDebugSessionTab tab = ((XDebugSessionImpl)session).getSessionTab();
            if (tab != null) {
               view = tab.getWatchesView();
            }
          }

          Producer<Boolean> isInExecutionPointHighlight = () -> {
            XSourcePosition debuggerPosition = session.getCurrentPosition();
            if (debuggerPosition != null) {
              return position.getFile().equals(debuggerPosition.getFile())
                     && position.getLine() == debuggerPosition.getLine()
                     && ((XDebuggerManagerImpl)XDebuggerManager.getInstance(session.getProject())).isFullLineHighlighter();
            }
            return false;
          };
          InlineDebugRenderer renderer = new InlineDebugRenderer(variablePresentation, valueNode, view, isInExecutionPointHighlight, onClick);
          Inlay<InlineDebugRenderer> inlay = ((InlayModelImpl)e.getInlayModel()).addAfterLineEndDebuggerHint(offset, customNode, renderer);
          if (customNode) {
            ((InlineWatchNodeImpl)valueNode).inlayCreated(inlay);
          }
        }
      });
      return true;
    }
    return false;
  }


  public static void createInlay(@NotNull Project project, @NotNull VirtualFile file, int offset, String inlayText) {
    UIUtil.invokeLaterIfNeeded(() -> {
      FileEditor editor = FileEditorManager.getInstance(project).getSelectedEditor(file);
      if (editor instanceof TextEditor) {
        Editor e = ((TextEditor)editor).getEditor();
        CharSequence text = e.getDocument().getImmutableCharSequence();
        int insertOffset = getIdentifierEndOffset(text, offset);
        e.getInlayModel().getInlineElementsInRange(insertOffset, insertOffset, MyRenderer.class).forEach(Disposer::dispose);
        e.getInlayModel().addInlineElement(insertOffset, new MyRenderer(inlayText));
      }
    });
  }

  public static void clearInlays(@NotNull Project project) {
    UIUtil.invokeLaterIfNeeded(() -> {
      FileEditor[] editors = FileEditorManager.getInstance(project).getAllEditors();
      for (FileEditor editor : editors) {
        if (editor instanceof TextEditor) {
          Editor e = ((TextEditor)editor).getEditor();
          e.getInlayModel().getInlineElementsInRange(0, e.getDocument().getTextLength(), MyRenderer.class).forEach(Disposer::dispose);
          e.getInlayModel().getAfterLineEndElementsInRange(0, e.getDocument().getTextLength(), InlineDebugRenderer.class).forEach(Disposer::dispose);
        }
      }
    });
  }

  public static void setupValuePlaceholders(@NotNull XDebugSessionImpl session, boolean removePlaceholders) {
    XSourcePosition position = removePlaceholders ? null : session.getCurrentPosition();
    if (!removePlaceholders && position == null) return;

    XDebuggerInlayUtil.Helper helper = session.getSessionData().getUserData(HELPER_KEY);
    if (helper == null) return;

    Project project = session.getProject();
    PsiDocumentManager.getInstance(project).performLaterWhenAllCommitted(() -> helper.setupValuePlaceholders(project, position));
  }

  public static boolean showValueInBlockInlay(@NotNull XDebugSessionImpl session,
                                              @NotNull XValueNodeImpl node,
                                              @NotNull XSourcePosition position) {
    XDebuggerInlayUtil.Helper helper = session.getSessionData().getUserData(HELPER_KEY);
    return helper != null && helper.showValueInBlockInlay(session.getProject(), node, position);
  }

  public static void createBlockInlay(@NotNull Editor editor, int offset) {
    editor.getInlayModel().addBlockElement(offset, false, false, 0, new MyBlockRenderer());
  }

  public static void addValueToBlockInlay(@NotNull Editor editor, int offset, String inlayText) {
    int lineStartOffset = DocumentUtil.getLineStartOffset(offset, editor.getDocument());
    List<Inlay<? extends MyBlockRenderer>>
      inlays = editor.getInlayModel().getBlockElementsInRange(lineStartOffset, lineStartOffset, MyBlockRenderer.class);
    if (inlays.size() != 1) return;
    Inlay<? extends MyBlockRenderer> inlay = inlays.get(0);
    CharSequence text = editor.getDocument().getImmutableCharSequence();
    int identifierEndOffset = getIdentifierEndOffset(text, offset);
    inlay.getRenderer().addValue(offset, identifierEndOffset, inlayText);
    inlay.update();
  }

  public static void clearBlockInlays(@NotNull Editor editor) {
    editor.getInlayModel().getBlockElementsInRange(0, editor.getDocument().getTextLength(), MyBlockRenderer.class)
      .forEach(Disposer::dispose);
  }

  public interface Helper {
    void setupValuePlaceholders(@NotNull Project project, @Nullable XSourcePosition currentPosition);
    boolean showValueInBlockInlay(@NotNull Project project, @NotNull XValueNodeImpl node, @NotNull XSourcePosition position);
  }

  private static class MyBlockRenderer implements EditorCustomElementRenderer  {
    private final SortedSet<ValueInfo> values = new TreeSet<>();

    void addValue(int refStartOffset, int refEndOffset, @NotNull String value) {
      ValueInfo info = new ValueInfo(refStartOffset, refEndOffset, value);
      values.remove(info);
      values.add(info); // retain latest reported value for given offset
    }

    @Override
    public int calcWidthInPixels(@NotNull Inlay inlay) {
      return 0;
    }

    @Override
    public void paint(@NotNull Inlay inlay,
                      @NotNull Graphics g,
                      @NotNull Rectangle targetRegion,
                      @NotNull TextAttributes textAttributes) {
      if (values.isEmpty()) return;
      Editor editor = inlay.getEditor();
      EditorColorsScheme colorsScheme = editor.getColorsScheme();
      TextAttributes attributes = colorsScheme.getAttributes(DebuggerColors.INLINED_VALUES_EXECUTION_LINE);
      if (attributes == null) return;
      Color fgColor = attributes.getForegroundColor();
      if (fgColor == null) return;
      g.setColor(fgColor);
      g.setFont(new Font(colorsScheme.getEditorFontName(), attributes.getFontType(), colorsScheme.getEditorFontSize()));

      int curX = 0;
      for (ValueInfo value : values) {
        curX += JBUIScale.scale(5); // minimum gap between values
        int xStart = editor.offsetToXY(value.refStartOffset, true, false).x;
        int xEnd = editor.offsetToXY(value.refEndOffset, false, true).x;
        int width = g.getFontMetrics().stringWidth(value.value);
        curX = Math.max(curX, (xStart + xEnd - width) / 2);
        g.drawString(value.value, curX, targetRegion.y + editor.getAscent());
        g.drawLine(MathUtil.clamp(curX + width / 2, xStart, xEnd), targetRegion.y, curX + width / 2, targetRegion.y + 2);
        g.drawLine(curX, targetRegion.y + 2, curX + width, targetRegion.y + 2);
        curX += width;
      }
    }

    private static final class ValueInfo implements Comparable<ValueInfo> {
      private final int refStartOffset;
      private final int refEndOffset;
      private final String value;

      private ValueInfo(int refStartOffset, int refEndOffset, String value) {
        this.refStartOffset = refStartOffset;
        this.refEndOffset = refEndOffset;
        this.value = value;
      }

      @Override
      public int compareTo(@NotNull ValueInfo o) {
        return refStartOffset - o.refStartOffset;
      }
    }
  }

  private static final class MyRenderer implements EditorCustomElementRenderer {
    private final String myText;

    private MyRenderer(String text) {
      myText = "(" + text + ")";
    }

    private static FontInfo getFontInfo(@NotNull Editor editor) {
      EditorColorsScheme colorsScheme = editor.getColorsScheme();
      FontPreferences fontPreferences = colorsScheme.getFontPreferences();
      TextAttributes attributes = editor.getColorsScheme().getAttributes(DebuggerColors.INLINED_VALUES_EXECUTION_LINE);
      int fontStyle = attributes == null ? Font.PLAIN : attributes.getFontType();
      return ComplementaryFontsRegistry.getFontAbleToDisplay('a', fontStyle, fontPreferences,
                                                             FontInfo.getFontRenderContext(editor.getContentComponent()));
    }

    @Override
    public int calcWidthInPixels(@NotNull Inlay inlay) {
      FontInfo fontInfo = getFontInfo(inlay.getEditor());
      return fontInfo.fontMetrics().stringWidth(myText);
    }

    @Override
    public void paint(@NotNull Inlay inlay, @NotNull Graphics g, @NotNull Rectangle r, @NotNull TextAttributes textAttributes) {
      Editor editor = inlay.getEditor();
      TextAttributes attributes = editor.getColorsScheme().getAttributes(DebuggerColors.INLINED_VALUES_EXECUTION_LINE);
      if (attributes == null) return;
      Color fgColor = attributes.getForegroundColor();
      if (fgColor == null) return;
      g.setColor(fgColor);
      FontInfo fontInfo = getFontInfo(editor);
      g.setFont(fontInfo.getFont());
      FontMetrics metrics = fontInfo.fontMetrics();
      g.drawString(myText, r.x, r.y + metrics.getAscent());
    }
  }
}
