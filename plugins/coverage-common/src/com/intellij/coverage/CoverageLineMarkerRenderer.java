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
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.markup.ActiveGutterRenderer;
import com.intellij.openapi.editor.markup.FillingLineMarkerRenderer;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.colors.pages.GeneralColorsPage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.ui.Gray;
import com.intellij.ui.HintHint;
import com.intellij.ui.JBColor;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
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
  private final @Nullable String myClassName;
  /**
   * Zero-based old line numbers to line data.
   */
  private final @NotNull TreeMap<Integer, LineData> myLines;
  private final boolean myCoverageByTestApplicable;
  private final Function<? super Integer, Integer> myOldToNewConverter;
  private final CoverageSuitesBundle myCoverageSuite;
  private final boolean mySubCoverageActive;
  /**
   * Zero-based line number from the original state of file.
   * Use {@link CoverageLineMarkerRenderer#myOldToNewConverter} to get line number in the current state of the document
   */
  private final int myOldLine;

  protected CoverageLineMarkerRenderer(int oldLine,
                                       @Nullable String className,
                                       @NotNull TreeMap<Integer, LineData> lines,
                                       boolean coverageByTestApplicable,
                                       Function<? super Integer, Integer> newToOldConverter,
                                       Function<? super Integer, Integer> oldToNewConverter,
                                       CoverageSuitesBundle coverageSuite, boolean subCoverageActive) {
    myKey = getAttributesKey(oldLine, lines);
    myOldLine = oldLine;
    myClassName = className;
    myLines = lines;
    myCoverageByTestApplicable = coverageByTestApplicable;
    myOldToNewConverter = oldToNewConverter;
    myCoverageSuite = coverageSuite;
    mySubCoverageActive = subCoverageActive;
  }

  @Override
  public @Nullable Icon getIcon() {
    final LineData lineData = getLineData(myOldLine);
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
                                                       @NotNull final TreeMap<Integer, LineData> lines,
                                                       final boolean coverageByTestApplicable,
                                                       @NotNull final CoverageSuitesBundle coverageSuite,
                                                       final Function<? super Integer, Integer> newToOldConverter,
                                                       final Function<? super Integer, Integer> oldToNewConverter,
                                                       boolean subCoverageActive) {
    return new CoverageLineMarkerRenderer(lineNumber, className, lines, coverageByTestApplicable, newToOldConverter,
                                          oldToNewConverter, coverageSuite, subCoverageActive);
  }

  public static TextAttributesKey getAttributesKey(final int oldLine,
                                                   @NotNull final TreeMap<Integer, LineData> lines) {

    return getAttributesKey(lines.get(oldLine));
  }

  private static TextAttributesKey getAttributesKey(LineData lineData) {
    int status = lineData == null ? LineCoverage.NONE : lineData.getStatus();
    return switch (status) {
      case LineCoverage.FULL -> CodeInsightColors.LINE_FULL_COVERAGE;
      case LineCoverage.PARTIAL -> CodeInsightColors.LINE_PARTIAL_COVERAGE;
      default -> CodeInsightColors.LINE_NONE_COVERAGE;
    };
  }

  private int oldToNew(int line) {
    return myOldToNewConverter == null ? line : myOldToNewConverter.fun(line).intValue();
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
    showHint(editor, myOldLine);
  }

  private void showHint(final Editor editor, final int oldLine) {
    int lineInCurrent = oldToNew(oldLine);
    if (lineInCurrent < 0) return;
    JBColor borderColor = new JBColor(Gray._206, Gray._75);
    final JPanel panel = new JPanel(new VerticalLayout(0));
    panel.setBorder(JBUI.Borders.customLine(borderColor));
    Disposable unregisterActionsDisposable = Disposer.newDisposable();
    panel.add(createActionsToolbar(editor, oldLine, unregisterActionsDisposable));

    final LineData lineData = getLineData(oldLine);
    final Editor uEditor;
    final String report;
    if (!mySubCoverageActive && (report = getReport(lineData, lineInCurrent, editor, myCoverageSuite)) != null) {
      final EditorFactory factory = EditorFactory.getInstance();
      final Document doc = factory.createDocument(report);
      doc.setReadOnly(true);
      uEditor = factory.createViewer(doc, editor.getProject(), EditorKind.PREVIEW);
      var component = EditorFragmentComponent.createEditorFragmentComponent(uEditor, 0, doc.getLineCount(), false, false);
      component.setBorder(JBUI.Borders.empty(4, 8));

      JPanel hintPanel = new JPanel(new BorderLayout());
      hintPanel.add(component);
      hintPanel.setBorder(JBUI.Borders.customLine(borderColor, 1, 0, 0, 0));
      panel.add(hintPanel);
    }
    else {
      uEditor = null;
    }

    final LightweightHint hint = new LightweightHint(panel) {
      @Override
      public void hide() {
        if (uEditor != null) EditorFactory.getInstance().releaseEditor(uEditor);
        Disposer.dispose(unregisterActionsDisposable);
        super.hide();
      }
    };
    int hideFlags = HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE |
                    HintManager.HIDE_BY_OTHER_HINT | HintManager.HIDE_BY_SCROLLING;
    HintHint hintInfo = new HintHint(editor, new Point());
    HintManagerImpl.getInstanceImpl().showGutterHint(hint, editor, lineInCurrent, THICKNESS, hideFlags, -1, false, hintInfo);
  }

  @Nullable
  public static String getReport(LineData lineData, int lineInCurrent, Editor editor, CoverageSuitesBundle bundle) {
    final Document document = editor.getDocument();
    final Project project = editor.getProject();
    assert project != null;

    final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (psiFile == null) return null;

    final int lineStartOffset = document.getLineStartOffset(lineInCurrent);
    final int lineEndOffset = document.getLineEndOffset(lineInCurrent);
    TextRange textRange = TextRange.create(lineStartOffset, lineEndOffset);
    return bundle.getCoverageEngine().generateBriefReport(bundle, editor, psiFile, textRange, lineData);
  }

  protected JComponent createActionsToolbar(final Editor editor, final int oldLine, Disposable parent) {

    final JComponent editorComponent = editor.getComponent();

    final DefaultActionGroup group = new DefaultActionGroup();
    final GotoPreviousCoveredLineAction prevAction = new GotoPreviousCoveredLineAction(editor, oldLine);
    final GotoNextCoveredLineAction nextAction = new GotoNextCoveredLineAction(editor, oldLine);

    group.add(prevAction);
    group.add(nextAction);

    prevAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_MASK | InputEvent.SHIFT_MASK)), editorComponent);
    nextAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_MASK | InputEvent.SHIFT_MASK)), editorComponent);
    Disposer.register(parent, () -> {
      prevAction.unregisterCustomShortcutSet(editorComponent);
      nextAction.unregisterCustomShortcutSet(editorComponent);
    });

    final LineData lineData = getLineData(oldLine);
    if (myCoverageByTestApplicable) {
      group.add(new ShowCoveringTestsAction(editor.getProject(), myCoverageSuite, myClassName, lineData));
    }
    final AnAction byteCodeViewAction = ActionManager.getInstance().getAction("ByteCodeViewer");
    if (byteCodeViewAction != null) {
      group.add(byteCodeViewAction);
    }
    group.add(new EditCoverageColorsAction(editor, oldLine));
    group.add(new HideCoverageInfoAction());

    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("CoverageHintToolbar", group, true);
    toolbar.setTargetComponent(editorComponent);
    return toolbar.getComponent();
  }

  public void moveToLine(final int lineNumber, final Editor editor) {
    final int firstOffset = editor.getDocument().getLineStartOffset(lineNumber);
    editor.getCaretModel().moveToOffset(firstOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);

    editor.getScrollingModel().runActionOnScrollingFinished(() -> showHint(editor, lineNumber));
  }

  @Nullable
  public LineData getLineData(int oldLine) {
    return myLines.get(oldLine);
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
    private final int myOldLine;

    BaseGotoCoveredLineAction(final Editor editor, final int oldLine) {
      myEditor = editor;
      myOldLine = oldLine;
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
      final Integer nextOldLine = getLineEntry();
      if (nextOldLine != null) {
        moveToLine(nextOldLine.intValue(), myEditor);
      }
    }

    protected abstract int next(int idx, int size);

    /**
     * @return next interesting old line
     */
    @Nullable
    private Integer getLineEntry() {
      List<Integer> oldLines = ContainerUtil.sorted(myLines.keySet());
      int size = oldLines.size();
      final LineData data = getLineData(myOldLine);
      final int currentStatus = data != null ? data.getStatus() : LineCoverage.NONE;
      int idx = oldLines.indexOf(myOldLine);
      if (idx < 0) {
        return null;
      }
      while (true) {
        idx = next(idx, size);
        int nextLine = oldLines.get(idx);
        if (nextLine == myOldLine) return null;
        final LineData lineData = getLineData(nextLine);
        if (lineData != null && lineData.getStatus() != currentStatus) {
          if (oldToNew(nextLine) >= 0) {
            return nextLine;
          }
        }
      }
    }

    @Nullable
    protected String getNextChange() {
      Integer nextOldLine = getLineEntry();
      if (nextOldLine != null) {
        final LineData lineData = getLineData(nextOldLine);
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
      final ColorAndFontOptions colorAndFontOptions = new ColorAndFontOptions() {
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
