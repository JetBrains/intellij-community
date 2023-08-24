// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
import com.intellij.openapi.editor.markup.FillingLineMarkerRenderer;
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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

public class CoverageLineMarkerRenderer implements ActiveGutterRenderer, FillingLineMarkerRenderer, LineMarkerRendererWithErrorStripe {
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

  private int getLineNumber() {
    return myLineNumber;
  }

  @Override
  public @Nullable Icon getIcon() {
    final LineData lineData = getLineData(getLineNumber());
    if (lineData != null && lineData.isCoveredByOneTest()) {
      return AllIcons.Gutter.Unique;
    }
    return null;
  }

  @Override
  public @NotNull TextAttributesKey getTextAttributesKey() {
    return myKey;
  }

  @Nullable
  @Override
  public Integer getMaxWidth() {
    return THICKNESS;
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
    int status = lineData == null ? LineCoverage.NONE : lineData.getStatus();
    return switch (status) {
      case LineCoverage.FULL -> CodeInsightColors.LINE_FULL_COVERAGE;
      case LineCoverage.PARTIAL -> CodeInsightColors.LINE_PARTIAL_COVERAGE;
      default -> CodeInsightColors.LINE_NONE_COVERAGE;
    };
  }

  @Override
  public boolean canDoAction(@NotNull final MouseEvent e) {
    Component component = e.getComponent();
    if (component instanceof EditorGutterComponentEx gutter) {
      return e.getX() > gutter.getLineMarkerAreaOffset() && e.getX() < gutter.getIconAreaOffset();
    }
    return false;
  }

  @Override
  public void doAction(@NotNull final Editor editor, @NotNull final MouseEvent e) {
    e.consume();
    showHint(editor, getLineNumber());
  }

  private void showHint(final Editor editor, final int lineNumber) {
    final JPanel panel = new JPanel(new BorderLayout());
    Disposable unregisterActionsDisposable = Disposer.newDisposable();
    panel.add(createActionsToolbar(editor, lineNumber, unregisterActionsDisposable), BorderLayout.NORTH);

    final LineData lineData = getLineData(lineNumber);
    final EditorImpl uEditor;
    final String report;
    if (lineData != null && lineData.getStatus() != LineCoverage.NONE && !mySubCoverageActive &&
        (report = getReport(editor, lineNumber)) != null) {
      final EditorFactory factory = EditorFactory.getInstance();
      final Document doc = factory.createDocument(report);
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
    int hideFlags = HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_OTHER_HINT | HintManager.HIDE_BY_SCROLLING;
    HintHint hintInfo = new HintHint(editor, new Point());
    HintManagerImpl.getInstanceImpl().showGutterHint(hint, editor, lineNumber, THICKNESS, hideFlags, -1, false, hintInfo);
  }

  @Nullable
  private String getReport(final Editor editor, final int lineNumber) {
    final LineData lineData = getLineData(lineNumber);

    final Document document = editor.getDocument();
    final Project project = editor.getProject();
    assert project != null;

    final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (psiFile == null) return null;

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
      group.add(new ShowCoveringTestsAction(editor.getProject(), myClassName, lineData));
    }
    final AnAction byteCodeViewAction = ActionManager.getInstance().getAction("ByteCodeViewer");
    if (byteCodeViewAction != null) {
      group.add(byteCodeViewAction);
    }
    group.add(new EditCoverageColorsAction(editor, lineNumber));
    group.add(new HideCoverageInfoAction());

    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("CoverageHintToolbar", group, true);
    toolbar.setTargetComponent(editorComponent);
    final JComponent toolbarComponent = toolbar.getComponent();

    final Color background = ((EditorEx)editor).getBackgroundColor();
    final Color foreground = editor.getColorsScheme().getColor(EditorColors.CARET_COLOR);
    toolbarComponent.setBackground(background);
    toolbarComponent.setBorder(new ColoredSideBorder(foreground, foreground, lineData == null || lineData.getStatus() == LineCoverage.NONE || mySubCoverageActive ? foreground : null, foreground, 1));
    return toolbarComponent;
  }

  public void moveToLine(final int lineNumber, final Editor editor) {
    final int firstOffset = editor.getDocument().getLineStartOffset(lineNumber);
    editor.getCaretModel().moveToOffset(firstOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);

    editor.getScrollingModel().runActionOnScrollingFinished(() -> showHint(editor, lineNumber));
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
      List<Integer> list = ContainerUtil.sorted(myLines.keySet());
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
          return switch (lineData.getStatus()) {
            case LineCoverage.NONE -> CoverageBundle.message("coverage.next.change.uncovered");
            case LineCoverage.PARTIAL -> CoverageBundle.message("coverage.next.change.partial.covered");
            case LineCoverage.FULL -> CoverageBundle.message("coverage.next.change.fully.covered");
            default -> null;
          };
        }
      }
      return null;
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
      e.getPresentation().setEnabled(getLineEntry() != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
  }

  private final class EditCoverageColorsAction extends AnAction {
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

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
  }

  @NotNull
  @Override
  public String getAccessibleName() {
    return CoverageBundle.message("marker.code.coverage");
  }
}
