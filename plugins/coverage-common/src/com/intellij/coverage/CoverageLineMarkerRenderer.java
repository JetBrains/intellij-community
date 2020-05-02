/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.coverage;

import com.intellij.application.options.colors.ColorAndFontOptions;
import com.intellij.application.options.colors.ColorAndFontPanelFactory;
import com.intellij.application.options.colors.NewColorAndFontPanel;
import com.intellij.application.options.colors.SimpleEditorPreview;
import com.intellij.codeInsight.hint.EditorFragmentComponent;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.coverage.actions.HideCoverageInfoAction;
import com.intellij.coverage.actions.ShowCoveringTestsAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.ActiveGutterRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.colors.pages.GeneralColorsPage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.ui.ColoredSideBorder;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

/**
 * @author ven
 */
public class CoverageLineMarkerRenderer implements ActiveGutterRenderer, LineMarkerRendererWithErrorStripe {
  private static final int THICKNESS = 8;
  private final TextAttributesKey myKey;
  private final String myClassName;
  private final TreeMap<Integer, LineData> myLines;
  private final boolean myCoverageByTestApplicable;
  private final Function<? super Integer, Integer> myNewToOldConverter;
  private final Function<? super Integer, Integer> myOldToNewConverter;
  private final CoverageSuitesBundle myCoverageSuite;
  private final boolean mySubCoverageActive;
  private final int myLineNumber;

  protected CoverageLineMarkerRenderer(final int lineNumber, @Nullable final String className, final TreeMap<Integer, LineData> lines,
                             final boolean coverageByTestApplicable,
                             final Function<? super Integer, Integer> newToOldConverter,
                             final Function<? super Integer, Integer> oldToNewConverter,
                             final CoverageSuitesBundle coverageSuite, boolean subCoverageActive) {
    myKey = getAttributesKey(lineNumber, lines);
    myLineNumber = lineNumber;
    myClassName = className;
    myLines = lines;
    myCoverageByTestApplicable = coverageByTestApplicable;
    myNewToOldConverter = newToOldConverter;
    myOldToNewConverter = oldToNewConverter;
    myCoverageSuite = coverageSuite;
    mySubCoverageActive = subCoverageActive;
  }

  private int getCurrentLineNumber(@NotNull Editor editor, Point mousePosition) {
    if (myLineNumber > -1) return myLineNumber;
    return editor.xyToLogicalPosition(mousePosition).line;
  }
  
  @Override
  public void paint(@NotNull Editor editor, @NotNull Graphics g, @NotNull Rectangle r) {
    final TextAttributes color = editor.getColorsScheme().getAttributes(myKey);
    Color bgColor = color.getBackgroundColor();
    if (bgColor == null) {
      bgColor = color.getForegroundColor();
    }
    if (bgColor != null) {
      g.setColor(bgColor);
    }
    g.fillRect(r.x, r.y, r.width, r.height);
    final LineData lineData = getLineData(getCurrentLineNumber(editor, new Point(0, r.y)));
    if (lineData != null && lineData.isCoveredByOneTest()) {
      AllIcons.Gutter.Unique.paintIcon(editor.getComponent(), g, r.x, r.y);
    }
  }

  public static CoverageLineMarkerRenderer getRenderer(int lineNumber,
                                                       @Nullable final String className,
                                                       final TreeMap<Integer, LineData> lines,
                                                       final boolean coverageByTestApplicable,
                                                       @NotNull final CoverageSuitesBundle coverageSuite,
                                                       final Function<? super Integer, Integer> newToOldConverter,
                                                       final Function<? super Integer, Integer> oldToNewConverter, boolean subCoverageActive) {
    return new CoverageLineMarkerRenderer(lineNumber, className, lines, coverageByTestApplicable, newToOldConverter,
                                          oldToNewConverter, coverageSuite, subCoverageActive);
  }

  public static TextAttributesKey getAttributesKey(final int lineNumber,
                                                   final TreeMap<Integer, LineData> lines) {

    return getAttributesKey(lines.get(lineNumber));
  }

  private static TextAttributesKey getAttributesKey(LineData lineData) {
    if (lineData != null) {
      switch (lineData.getStatus()) {
        case LineCoverage.FULL:
          return CodeInsightColors.LINE_FULL_COVERAGE;
        case LineCoverage.PARTIAL:
          return CodeInsightColors.LINE_PARTIAL_COVERAGE;
      }
    }

    return CodeInsightColors.LINE_NONE_COVERAGE;
  }

  @Override
  public boolean canDoAction(@NotNull final MouseEvent e) {
    Component component = e.getComponent();
    if (component instanceof EditorGutterComponentEx) {
      EditorGutterComponentEx gutter = (EditorGutterComponentEx)component;
      return e.getX() > gutter.getLineMarkerAreaOffset() && e.getX() < gutter.getIconAreaOffset();
    }
    return false;
  }

  @Override
  public void doAction(@NotNull final Editor editor, @NotNull final MouseEvent e) {
    e.consume();
    final JComponent comp = (JComponent)e.getComponent();
    final JRootPane rootPane = comp.getRootPane();
    final JLayeredPane layeredPane = rootPane.getLayeredPane();
    final Point point = SwingUtilities.convertPoint(comp, THICKNESS, e.getY(), layeredPane);
    showHint(editor, point, getCurrentLineNumber(editor, e.getPoint()));
  }

  private void showHint(final Editor editor, final Point mousePosition, final int lineNumber) {
    final JPanel panel = new JPanel(new BorderLayout());
    Disposable unregisterActionsDisposable = new Disposable() {
      @Override
      public void dispose() { }
    };
    panel.add(createActionsToolbar(editor, lineNumber, unregisterActionsDisposable), BorderLayout.NORTH);

    final LineData lineData = getLineData(lineNumber);
    final EditorImpl uEditor;
    if (lineData != null && lineData.getStatus() != LineCoverage.NONE && !mySubCoverageActive) {
      final EditorFactory factory = EditorFactory.getInstance();
      final Document doc = factory.createDocument(getReport(editor, lineNumber));
      doc.setReadOnly(true);
      uEditor = (EditorImpl)factory.createEditor(doc, editor.getProject());
      panel.add(EditorFragmentComponent.createEditorFragmentComponent(uEditor, 0, doc.getLineCount(), false, false), BorderLayout.CENTER);
    } else {
      uEditor = null;
    }


    final LightweightHint hint = new LightweightHint(panel){
      @Override
      public void hide() {
        if (uEditor != null) EditorFactory.getInstance().releaseEditor(uEditor);
        Disposer.dispose(unregisterActionsDisposable);
        super.hide();

      }
    };
    Point point = HintManagerImpl.getHintPosition(hint, editor, new LogicalPosition(lineNumber, 0), HintManager.UNDER);
    if (mousePosition != null) {
      point.x = mousePosition.x;
      point.y = mousePosition.y + Math.abs(point.y - mousePosition.y) % editor.getLineHeight() ;
    }
    else {
      Point p = editor.visualPositionToXY(editor.offsetToVisualPosition(0));
      EditorGutterComponentEx editorComponent = (EditorGutterComponentEx)editor.getGutter();
      JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();
      point.x = SwingUtilities.convertPoint(editorComponent, THICKNESS, p.y, layeredPane).x;
    }
    HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, point,
                                                     HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_OTHER_HINT | HintManager.HIDE_BY_SCROLLING, -1, false, new HintHint(editor, point));
  }

  private String getReport(final Editor editor, final int lineNumber) {
    final LineData lineData = getLineData(lineNumber);

    final Document document = editor.getDocument();
    final Project project = editor.getProject();
    assert project != null;

    final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    assert psiFile != null;

    final int lineStartOffset = document.getLineStartOffset(lineNumber);
    final int lineEndOffset = document.getLineEndOffset(lineNumber);

    return myCoverageSuite.getCoverageEngine().generateBriefReport(editor, psiFile, lineNumber, lineStartOffset, lineEndOffset, lineData);
  }

  protected JComponent createActionsToolbar(final Editor editor, final int lineNumber, Disposable parent) {

    final JComponent editorComponent = editor.getComponent();

    final DefaultActionGroup group = new DefaultActionGroup();
    final GotoPreviousCoveredLineAction prevAction = new GotoPreviousCoveredLineAction(editor, lineNumber);
    final GotoNextCoveredLineAction nextAction = new GotoNextCoveredLineAction(editor, lineNumber);

    group.add(prevAction);
    group.add(nextAction);

    prevAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_MASK|InputEvent.SHIFT_MASK)), editorComponent);
    nextAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_MASK|InputEvent.SHIFT_MASK)), editorComponent);
    Disposer.register(parent, () -> {
      prevAction.unregisterCustomShortcutSet(editorComponent);
      nextAction.unregisterCustomShortcutSet(editorComponent);
    });

    final LineData lineData = getLineData(lineNumber);
    if (myCoverageByTestApplicable) {
      group.add(new ShowCoveringTestsAction(myClassName, lineData));
    }
    final AnAction byteCodeViewAction = ActionManager.getInstance().getAction("ByteCodeViewer");
    if (byteCodeViewAction != null) {
      group.add(byteCodeViewAction);
    }
    group.add(new EditCoverageColorsAction(editor, lineNumber));
    group.add(new HideCoverageInfoAction());

    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.FILEHISTORY_VIEW_TOOLBAR, group, true);
    final JComponent toolbarComponent = toolbar.getComponent();

    final Color background = ((EditorEx)editor).getBackgroundColor();
    final Color foreground = editor.getColorsScheme().getColor(EditorColors.CARET_COLOR);
    toolbarComponent.setBackground(background);
    toolbarComponent.setBorder(new ColoredSideBorder(foreground, foreground, lineData == null || lineData.getStatus() == LineCoverage.NONE || mySubCoverageActive ? foreground : null, foreground, 1));
    toolbar.updateActionsImmediately();
    return toolbarComponent;
  }

  public void moveToLine(final int lineNumber, final Editor editor) {
    final int firstOffset = editor.getDocument().getLineStartOffset(lineNumber);
    editor.getCaretModel().moveToOffset(firstOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);

    editor.getScrollingModel().runActionOnScrollingFinished(() -> showHint(editor, null, lineNumber));
  }

  @Nullable
  public LineData getLineData(int lineNumber) {
    return myLines != null ? myLines.get(myNewToOldConverter != null ? myNewToOldConverter.fun(lineNumber).intValue() : lineNumber) : null;
  }

  @Override
  public Color getErrorStripeColor(final Editor editor) {
    return editor.getColorsScheme().getAttributes(myKey).getErrorStripeColor();
  }

  @NotNull
  @Override
  public Position getPosition() {
    return Position.LEFT;
  }

  private class GotoPreviousCoveredLineAction extends BaseGotoCoveredLineAction {

    GotoPreviousCoveredLineAction(final Editor editor, final int lineNumber) {
      super(editor, lineNumber);
      ActionUtil.copyFrom(this, IdeActions.ACTION_PREVIOUS_OCCURENCE);
      getTemplatePresentation().setText(CoverageBundle.message("coverage.previous.mark"));
    }

    @Override
    protected int next(final int idx, int size) {
      if (idx <= 0) return size - 1;
      return idx - 1;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      final String nextChange = getNextChange();
      if (nextChange != null) {
        e.getPresentation().setText(CoverageBundle.message("coverage.previous.place", nextChange));
      }
    }
  }

  private class GotoNextCoveredLineAction extends BaseGotoCoveredLineAction {

    GotoNextCoveredLineAction(final Editor editor, final int lineNumber) {
      super(editor, lineNumber);
      copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_OCCURENCE));
      getTemplatePresentation().setText(CoverageBundle.message("coverage.next.mark"));
    }

    @Override
    protected int next(final int idx, int size) {
      if (idx == size - 1) return 0;
      return idx + 1;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      final String nextChange = getNextChange();
      if (nextChange != null) {
        e.getPresentation().setText(CoverageBundle.message("coverage.next.place", nextChange));
      }
    }
  }

  private abstract class BaseGotoCoveredLineAction extends AnAction {
    private final Editor myEditor;
    private final int myLineNumber;

    BaseGotoCoveredLineAction(final Editor editor, final int lineNumber) {
      myEditor = editor;
      myLineNumber = lineNumber;
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
      final Integer lineNumber = getLineEntry();
      if (lineNumber != null) {
        moveToLine(lineNumber.intValue(), myEditor);
      }
    }

    protected abstract int next(int idx, int size);

    @Nullable
    private Integer getLineEntry() {
      final ArrayList<Integer> list = new ArrayList<>(myLines.keySet());
      Collections.sort(list);
      int size = list.size();
      final LineData data = getLineData(myLineNumber);
      final int currentStatus = data != null ? data.getStatus() : LineCoverage.NONE;
      int idx = list.indexOf(myNewToOldConverter != null ? myNewToOldConverter.fun(myLineNumber).intValue() : myLineNumber);
      if (idx < 0) {
        return null;
      }
      while (true) {
        final int index = next(idx, size);
        Integer key = list.get(index);
        if (key == myLineNumber) return null;
        final LineData lineData = getLineData(key);
        idx = index;
        if (lineData != null && lineData.getStatus() != currentStatus) {
          final Integer line = list.get(idx);
          if (myOldToNewConverter != null) {
            final int newLine = myOldToNewConverter.fun(line).intValue();
            if (newLine != 0) return newLine;
          } else {
            return line;
          }
        }
      }
    }

    @Nullable
    protected String getNextChange() {
      Integer entry = getLineEntry();
      if (entry != null) {
        final LineData lineData = getLineData(entry);
        if (lineData != null) {
          switch (lineData.getStatus()) {
            case LineCoverage.NONE:
              return CoverageBundle.message("coverage.next.change.uncovered");
            case LineCoverage.PARTIAL:
              return CoverageBundle.message("coverage.next.change.partial.covered");
            case LineCoverage.FULL:
              return CoverageBundle.message("coverage.next.change.fully.covered");
          }
        }
      }
      return null;
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
      e.getPresentation().setEnabled(getLineEntry() != null);
    }
  }

  private class EditCoverageColorsAction extends AnAction {
    private final Editor myEditor;
    private final int myLineNumber;

    private EditCoverageColorsAction(Editor editor, int lineNumber) {
      super(CoverageBundle.message("coverage.edit.colors.action.name"), CoverageBundle.message("coverage.edit.colors.description"), AllIcons.General.Settings);
      myEditor = editor;
      myLineNumber = lineNumber;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setVisible(getLineData(myLineNumber) != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final GeneralColorsPage colorsPage = new GeneralColorsPage();
      String fullDisplayName = CoverageBundle
        .message("configurable.name.editor.colors.page", ApplicationBundle.message("title.colors.and.fonts"), colorsPage.getDisplayName());
      final ColorAndFontOptions colorAndFontOptions = new ColorAndFontOptions(){
        @Override
        protected List<ColorAndFontPanelFactory> createPanelFactories() {
          final ColorAndFontPanelFactory panelFactory = new ColorAndFontPanelFactory() {
            @NotNull
            @Override
            public NewColorAndFontPanel createPanel(@NotNull ColorAndFontOptions options) {
              final SimpleEditorPreview preview = new SimpleEditorPreview(options, colorsPage);
              return NewColorAndFontPanel.create(preview, colorsPage.getDisplayName(), options, null, colorsPage);
            }

            @NlsContexts.ConfigurableName
            @NotNull
            @Override
            public String getPanelDisplayName() {
              return fullDisplayName;
            }
          };
          return Collections.singletonList(panelFactory);
        }
      };
      final Configurable[] configurables = colorAndFontOptions.buildConfigurables();
      try {
        NewColorAndFontPanel page = colorAndFontOptions.findPage(fullDisplayName);
        final SearchableConfigurable general = colorAndFontOptions.findSubConfigurable(GeneralColorsPage.class);
        if (general != null && page != null) {
          final LineData lineData = getLineData(myLineNumber);
          ShowSettingsUtil.getInstance().editConfigurable(myEditor.getProject(), general,
                                                          () -> page.selectOptionByType(getAttributesKey(lineData).getExternalName()));
        }
      }
      finally {
        for (Configurable configurable : configurables) {
          configurable.disposeUIResources();
        }
        colorAndFontOptions.disposeUIResources();
      }
    }
  }

  @NotNull
  @Override
  public String getAccessibleName() {
    return CoverageBundle.message("marker.code.coverage");
  }
}
