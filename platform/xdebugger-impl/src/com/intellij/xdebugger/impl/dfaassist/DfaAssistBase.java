// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.dfaassist;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.hints.presentation.MenuOnClickPresentation;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.codeInsight.hints.presentation.PresentationRenderer;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayModel;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class DfaAssistBase implements Disposable {

  protected final Project myProject;
  protected volatile AssistMode myMode;
  // modified from EDT only
  private DfaAssistMarkup myMarkup = new DfaAssistMarkup(null, Collections.emptyList(), Collections.emptyList());

  public DfaAssistBase(Project project) {
    myProject = project;
  }

  @RequiresEdt
  protected void displayInlays(DfaResult result) {
    cleanUp();
    Map<PsiElement, DfaHint> hints = result.hints;
    Collection<TextRange> unreachable = result.unreachable;
    if (result.file == null) return;
    EditorImpl editor = ObjectUtils.tryCast(FileEditorManager.getInstance(myProject).getSelectedTextEditor(), EditorImpl.class);
    if (editor == null) return;
    VirtualFile expectedFile = result.file.getVirtualFile();
    if (expectedFile == null || !expectedFile.equals(editor.getVirtualFile())) return;
    List<Inlay<?>> newInlays = new ArrayList<>();
    List<RangeHighlighter> ranges = new ArrayList<>();
    AssistMode mode = myMode;
    if (!hints.isEmpty() && mode.displayInlays()) {
      InlayModel model = editor.getInlayModel();
      hints.forEach((expr, hint) -> {
        Segment range = expr.getTextRange();
        if (range == null) return;
        PresentationFactory factory = new PresentationFactory(editor);
        MenuOnClickPresentation presentation = new MenuOnClickPresentation(
          factory.roundWithBackground(factory.smallText(hint.getTitle())), myProject,
          () -> getInlayHintActions());
        newInlays.add(model.addInlineElement(range.getEndOffset(), new PresentationRenderer(presentation)));
      });
    }
    if (!unreachable.isEmpty() && mode.displayGrayOut()) {
      MarkupModelEx model = editor.getMarkupModel();
      for (TextRange range : unreachable) {
        RangeHighlighter highlighter = model.addRangeHighlighter(HighlightInfoType.UNUSED_SYMBOL.getAttributesKey(),
                                                                 range.getStartOffset(), range.getEndOffset(), HighlighterLayer.ERROR + 1,
                                                                 HighlighterTargetArea.EXACT_RANGE);
        ranges.add(highlighter);
      }
    }
    if (!newInlays.isEmpty() || !ranges.isEmpty()) {
      myMarkup = new DfaAssistMarkup(editor, newInlays, ranges);
    }
  }

  protected @NotNull List<@NotNull AnAction> getInlayHintActions() {
    return Collections.singletonList(new TurnOffDfaProcessorAction());
  }

  @Override
  public void dispose() {
    cleanUp();
  }

  protected void cleanUp() {
    UIUtil.invokeLaterIfNeeded(() -> {
      ReadAction.run(() -> Disposer.dispose(myMarkup));
    });
  }

  private static final class DfaAssistMarkup implements Disposable {
    private final @NotNull List<Inlay<?>> myInlays;
    private final @NotNull List<RangeHighlighter> myRanges;

    private DfaAssistMarkup(@Nullable Editor editor, @NotNull List<Inlay<?>> inlays, @NotNull List<RangeHighlighter> ranges) {
      myInlays = inlays;
      myRanges = ranges;
      if (editor != null) {
        editor.getDocument().addDocumentListener(new DocumentListener() {
          @Override
          public void beforeDocumentChange(@NotNull DocumentEvent event) {
            ApplicationManager.getApplication().invokeLater(() -> Disposer.dispose(DfaAssistMarkup.this));
          }
        }, this);
      }
    }

    @Override
    public void dispose() {
      ThreadingAssertions.assertEventDispatchThread();
      myInlays.forEach(Disposer::dispose);
      myInlays.clear();
      myRanges.forEach(RangeHighlighter::dispose);
      myRanges.clear();
    }
  }

  private final class TurnOffDfaProcessorAction extends AnAction {
    private TurnOffDfaProcessorAction() {
      super(XDebuggerBundle.message("action.TurnOffDfaAssist.text"),
            XDebuggerBundle.message("action.TurnOffDfaAssist.description"), AllIcons.Actions.Cancel);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent evt) {
      Disposer.dispose(DfaAssistBase.this);
    }
  }

  public enum AssistMode {
    NONE, INLAYS, GRAY_OUT, BOTH;

    boolean displayInlays() {
      return this == INLAYS || this == BOTH;
    }

    public boolean displayGrayOut() {
      return this == GRAY_OUT || this == BOTH;
    }
  }
}
