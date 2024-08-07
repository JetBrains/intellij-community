// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.patch.tool;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.diff.*;
import com.intellij.diff.actions.ProxyUndoRedoAction;
import com.intellij.diff.actions.impl.FocusOppositePaneAction;
import com.intellij.diff.actions.impl.SetEditorSettingsAction;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.merge.MergeModelBase;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.tools.fragmented.LineNumberConvertor;
import com.intellij.diff.tools.holders.TextEditorHolder;
import com.intellij.diff.tools.util.*;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil;
import com.intellij.diff.tools.util.side.TwosideContentPanel;
import com.intellij.diff.util.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterFreePainterAreaState;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.impl.LineNumberConverterAdapter;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.patch.AppliedTextPatch;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

class ApplyPatchViewer implements Disposable {
  @Nullable private final Project myProject;
  @NotNull private final DiffContext myContext;
  @NotNull private final ApplyPatchRequest myPatchRequest;

  @NotNull private final TextEditorHolder myResultHolder;
  @NotNull private final TextEditorHolder myPatchHolder;
  @NotNull private final EditorEx myResultEditor;
  @NotNull private final EditorEx myPatchEditor;

  @NotNull private final SimpleDiffPanel myPanel;
  @NotNull private final TwosideContentPanel myContentPanel;

  @NotNull private final MyModel myModel;

  @NotNull private final FocusTrackerSupport<Side> myFocusTrackerSupport;
  @NotNull private final MyPrevNextDifferenceIterable myPrevNextDifferenceIterable;
  @NotNull private final StatusPanel myStatusPanel;
  @NotNull private final MyFoldingModel myFoldingModel;

  @NotNull private final SetEditorSettingsAction myEditorSettingsAction;

  // Changes with known AppliedTo. Ordered as in result-editor
  @NotNull private final List<ApplyPatchChange> myResultChanges = new ArrayList<>();
  // All changes. Ordered as in patch-editor
  @NotNull private final List<ApplyPatchChange> myPatchChanges = new ArrayList<>();
  // All changes. Ordered as in result-editor. Non-applied changes are at the very beginning with model ranges [-1. -1)
  @NotNull private final List<ApplyPatchChange> myModelChanges = new ArrayList<>();

  private boolean myDisposed;

  ApplyPatchViewer(@NotNull DiffContext context, @NotNull ApplyPatchRequest request) {
    myProject = context.getProject();
    myContext = context;
    myPatchRequest = request;


    DocumentContent resultContent = request.getResultContent();
    DocumentContent patchContent = DiffContentFactory.getInstance().create(EditorFactory.getInstance().createDocument(""), resultContent);

    myResultHolder = TextEditorHolder.create(myProject, resultContent);
    myPatchHolder = TextEditorHolder.create(myProject, patchContent);

    myResultEditor = myResultHolder.getEditor();
    myPatchEditor = myPatchHolder.getEditor();

    if (isReadOnly()) myResultEditor.setViewer(true);
    myPatchEditor.setViewer(true);

    DiffUtil.disableBlitting(myResultEditor);
    DiffUtil.disableBlitting(myPatchEditor);

    ((EditorMarkupModel)myResultEditor.getMarkupModel()).setErrorStripeVisible(false);
    myResultEditor.setVerticalScrollbarOrientation(EditorEx.VERTICAL_SCROLLBAR_LEFT);

    myPatchEditor.getGutterComponentEx().setRightFreePaintersAreaState(EditorGutterFreePainterAreaState.SHOW);
    ((EditorMarkupModel)myPatchEditor.getMarkupModel()).setErrorStripeVisible(false);


    List<TextEditorHolder> holders = Arrays.asList(myResultHolder, myPatchHolder);
    List<EditorEx> editors = Arrays.asList(myResultEditor, myPatchEditor);
    JComponent resultTitle = DiffUtil.createTitle(myPatchRequest.getResultTitle());
    JComponent patchTitle = DiffUtil.createTitle(myPatchRequest.getPatchTitle());
    List<JComponent> titleComponents = Arrays.asList(resultTitle, patchTitle);

    myContentPanel = TwosideContentPanel.createFromHolders(holders);
    myContentPanel.setTitles(titleComponents);
    myPanel = new SimpleDiffPanel(myContentPanel, myContext) {
      @Override
      public void uiDataSnapshot(@NotNull DataSink sink) {
        super.uiDataSnapshot(sink);
        sink.set(CommonDataKeys.PROJECT, myProject);
        sink.set(DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE, myPrevNextDifferenceIterable);
      }
    };

    myModel = new MyModel(myProject, myResultEditor.getDocument());

    myFocusTrackerSupport = new FocusTrackerSupport.Twoside(holders);
    myFocusTrackerSupport.setCurrentSide(Side.LEFT);
    myPrevNextDifferenceIterable = new MyPrevNextDifferenceIterable();
    myStatusPanel = new MyStatusPanel();
    myFoldingModel = new MyFoldingModel(myProject, myResultEditor, this);


    DiffUtil.installLineConvertor(myResultEditor, myFoldingModel);

    new MyFocusOppositePaneAction().install(myPanel);
    new TextDiffViewerUtil.EditorActionsPopup(createEditorPopupActions()).install(editors, myPanel);

    new TextDiffViewerUtil.EditorFontSizeSynchronizer(editors).install(this);

    myEditorSettingsAction = new SetEditorSettingsAction(getTextSettings(), editors);
    myEditorSettingsAction.applyDefaults();

    ProxyUndoRedoAction.register(myProject, myResultEditor, myContentPanel);
  }

  @NotNull
  protected List<AnAction> createToolbarActions() {
    List<AnAction> group = new ArrayList<>();

    if (!isReadOnly()) {
      group.add(new MyToggleExpandByDefaultAction());
      group.add(myEditorSettingsAction);
      group.add(Separator.getInstance());
      group.add(new ShowDiffWithLocalAction());
      group.add(new ApplyNonConflictsAction());
    }

    return group;
  }

  @NotNull
  private List<AnAction> createEditorPopupActions() {
    List<AnAction> group = new ArrayList<>();

    if (!isReadOnly()) {
      group.add(new ApplySelectedChangesAction());
      group.add(new IgnoreSelectedChangesAction());
    }

    group.add(Separator.getInstance());
    group.addAll(TextDiffViewerUtil.createEditorPopupActions());

    return group;
  }

  @Override
  public void dispose() {
    if (myDisposed) return;
    myDisposed = true;

    Disposer.dispose(myModel);

    Disposer.dispose(myResultHolder);
    Disposer.dispose(myPatchHolder);
  }

  //
  // Getters
  //

  public boolean isReadOnly() {
    return !DiffUtil.canMakeWritable(myResultEditor.getDocument());
  }

  @NotNull
  public MyModel getModel() {
    return myModel;
  }

  @NotNull
  public List<ApplyPatchChange> getModelChanges() {
    return myModelChanges;
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  @NotNull
  public StatusPanel getStatusPanel() {
    return myStatusPanel;
  }

  @NotNull
  public JComponent getComponent() {
    return myPanel;
  }

  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return myResultEditor.getContentComponent();
  }

  @NotNull
  public EditorEx getResultEditor() {
    return myResultEditor;
  }

  @NotNull
  public EditorEx getPatchEditor() {
    return myPatchEditor;
  }

  @NotNull
  public Side getCurrentSide() {
    return myFocusTrackerSupport.getCurrentSide();
  }

  @NotNull
  public List<ApplyPatchChange> getPatchChanges() {
    return myPatchChanges;
  }

  @NotNull
  public TextDiffSettings getTextSettings() {
    return TextDiffSettings.getSettings("ApplyPatch"); //NON-NLS
  }

  @NotNull
  public FoldingModelSupport.Settings getFoldingModelSettings() {
    TextDiffSettings settings = getTextSettings();
    return new FoldingModelSupport.Settings(settings.getContextRange(), settings.isExpandByDefault());
  }

  //
  // Impl
  //

  protected void initPatchViewer() {
    myPanel.setPersistentNotifications(DiffUtil.createCustomNotifications(null, myContext, myPatchRequest));
    final Document outputDocument = myResultEditor.getDocument();
    boolean success =
      DiffUtil.executeWriteCommand(outputDocument, myProject, DiffBundle.message("message.init.merge.content.command"), () -> {
        outputDocument.setText(myPatchRequest.getLocalContent());
        if (!isReadOnly()) DiffUtil.putNonundoableOperation(myProject, outputDocument);
      });
    if (!success && !StringUtil.equals(outputDocument.getText(), myPatchRequest.getLocalContent())) {
      myPanel.setErrorContent(VcsBundle.message("patch.apply.display.local.content.was.modified.error"));
      return;
    }


    PatchChangeBuilder.AppliedPatchState state = new PatchChangeBuilder().buildFromApplied(myPatchRequest.getPatch().getHunks());


    Document patchDocument = myPatchEditor.getDocument();
    WriteAction.run(() -> patchDocument.setText(state.getPatchContent()));

    LineNumberConvertor convertor1 = state.getLineConvertor1();
    LineNumberConvertor convertor2 = state.getLineConvertor2();
    myPatchEditor.getGutter().setLineNumberConverter(new LineNumberConverterAdapter(convertor1.createConvertor()),
                                                     new LineNumberConverterAdapter(convertor2.createConvertor()));

    state.getSeparatorLines().forEach(line -> {
      int offset = patchDocument.getLineStartOffset(line);
      DiffDrawUtil.createLineSeparatorHighlighter(myPatchEditor, offset, offset);
    });

    List<PatchChangeBuilder.AppliedHunk> hunks = state.getHunks();

    int[] modelToPatchIndexes = DiffUtil.getSortedIndexes(hunks, (h1, h2) -> {
      LineRange lines1 = h1.getAppliedToLines();
      LineRange lines2 = h2.getAppliedToLines();
      if (lines1 == null && lines2 == null) return 0;
      if (lines1 == null) return -1;
      if (lines2 == null) return 1;
      return lines1.start - lines2.start;
    });
    int[] patchToModelIndexes = DiffUtil.invertIndexes(modelToPatchIndexes);

    List<LineRange> modelRanges = new ArrayList<>();
    for (int modelIndex = 0; modelIndex < hunks.size(); modelIndex++) {
      int patchIndex = modelToPatchIndexes[modelIndex];
      PatchChangeBuilder.AppliedHunk hunk = hunks.get(patchIndex);
      LineRange resultRange = hunk.getAppliedToLines();

      ApplyPatchChange change = new ApplyPatchChange(hunk, modelIndex, this);

      myModelChanges.add(change);
      if (resultRange != null) myResultChanges.add(change);

      modelRanges.add(resultRange != null ? resultRange : new LineRange(-1, -1));
    }
    myModel.setChanges(modelRanges);

    for (int index : patchToModelIndexes) {
      myPatchChanges.add(myModelChanges.get(index));
    }


    myFoldingModel.install(myResultChanges, getFoldingModelSettings());

    for (ApplyPatchChange change : myModelChanges) {
      change.reinstallHighlighters();
    }
    myStatusPanel.update();


    myContentPanel.setPainter(new MyDividerPainter());

    VisibleAreaListener areaListener = (e) -> myContentPanel.repaint();
    myResultEditor.getScrollingModel().addVisibleAreaListener(areaListener);
    myPatchEditor.getScrollingModel().addVisibleAreaListener(areaListener);

    myPatchEditor.getGutterComponentEx().revalidateMarkup();


    if (myResultChanges.size() > 0) {
      scrollToChange(myResultChanges.get(0), Side.LEFT, true);
    }
  }

  public void scrollToChange(@NotNull ApplyPatchChange change, @NotNull Side masterSide, boolean forceScroll) {
    if (change.getResultRange() == null) {
      DiffUtil.moveCaret(myPatchEditor, change.getPatchRange().start);
      myPatchEditor.getScrollingModel().scrollToCaret(forceScroll ? ScrollType.CENTER : ScrollType.MAKE_VISIBLE);
    }
    else {
      LineRange resultRange = change.getResultRange();
      LineRange patchRange = change.getPatchAffectedRange();

      int topShift = -1;
      if (!forceScroll) {
        int masterLine = masterSide.select(resultRange.start, patchRange.start);
        EditorEx masterEditor = masterSide.select(myResultEditor, myPatchEditor);
        int targetY = masterEditor.logicalPositionToXY(new LogicalPosition(masterLine, 0)).y;
        int scrollOffset = masterEditor.getScrollingModel().getVerticalScrollOffset();
        topShift = targetY - scrollOffset;
      }

      int[] offsets = SyncScrollSupport.getTargetOffsets(myResultEditor, myPatchEditor,
                                                         resultRange.start, resultRange.end,
                                                         patchRange.start, patchRange.end,
                                                         topShift);

      DiffUtil.moveCaret(myResultEditor, resultRange.start);
      DiffUtil.moveCaret(myPatchEditor, patchRange.start);

      DiffUtil.scrollToPoint(myResultEditor, new Point(0, offsets[0]), false);
      DiffUtil.scrollToPoint(myPatchEditor, new Point(0, offsets[1]), false);
    }
  }

  //
  // Modification operations
  //

  public void repaintDivider() {
    myContentPanel.repaintDivider();
  }

  public boolean executeCommand(@Nullable @NlsContexts.Command String commandName,
                                @NotNull final Runnable task) {
    return myModel.executeMergeCommand(commandName, null, UndoConfirmationPolicy.DEFAULT, false, null, task);
  }

  class MyModel extends MergeModelBase<ApplyPatchChange.State> {
    MyModel(@Nullable Project project, @NotNull Document document) {
      super(project, document);
    }

    @Override
    protected void reinstallHighlighters(int index) {
      ApplyPatchChange change = myModelChanges.get(index);
      change.reinstallHighlighters();
    }

    @NotNull
    @Override
    protected ApplyPatchChange.State storeChangeState(int index) {
      ApplyPatchChange change = myModelChanges.get(index);
      return change.storeState();
    }

    @Override
    protected void restoreChangeState(@NotNull ApplyPatchChange.State state) {
      super.restoreChangeState(state);
      ApplyPatchChange change = myModelChanges.get(state.myIndex);

      boolean wasResolved = change.isResolved();
      change.restoreState(state);
      if (wasResolved != change.isResolved()) onChangeResolved();
    }
  }

  protected void onChangeResolved() {
    if (isDisposed()) return;
    myStatusPanel.update();
  }

  public void markChangeResolved(@NotNull ApplyPatchChange change) {
    if (change.isResolved()) return;

    change.setResolved(true);
    myModel.invalidateHighlighters(change.getIndex());
    onChangeResolved();
  }

  public void replaceChange(@NotNull ApplyPatchChange change) {
    LineRange resultRange = change.getResultRange();
    LineRange patchRange = change.getPatchInsertionRange();
    if (resultRange == null || change.isResolved()) return;
    if (change.getStatus() != AppliedTextPatch.HunkStatus.EXACTLY_APPLIED) return;

    List<String> newContent = DiffUtil.getLines(myPatchEditor.getDocument(), patchRange.start, patchRange.end);
    myModel.replaceChange(change.getIndex(), newContent);

    markChangeResolved(change);
  }

  public void copyChangeToClipboard(@NotNull ApplyPatchChange change) {
    LineRange patchRange = change.getPatchInsertionRange();
    CharSequence newContent = DiffUtil.getLinesContent(myPatchEditor.getDocument(), patchRange.start, patchRange.end);

    CopyPasteManager.copyTextToClipboard(newContent.toString());

    myPatchEditor.getCaretModel().moveToOffset(myPatchEditor.getDocument().getLineStartOffset(patchRange.start));
    HintManager.getInstance().showInformationHint(myPatchEditor, DiffBundle.message("patch.dialog.copy.change.command.balloon"), HintManager.UNDER);
  }

  private final class ApplySelectedChangesAction extends ApplySelectedChangesActionBase {
    private ApplySelectedChangesAction() {
      getTemplatePresentation().setText(VcsBundle.messagePointer("action.presentation.ApplySelectedChangesAction.text"));
      getTemplatePresentation().setIcon(AllIcons.Actions.Checked);
      copyShortcutFrom(ActionManager.getInstance().getAction("Diff.ApplyRightSide"));
    }

    @Override
    protected boolean isEnabled(@NotNull ApplyPatchChange change) {
      return !change.isResolved() && change.getStatus() == AppliedTextPatch.HunkStatus.EXACTLY_APPLIED;
    }

    @Override
    protected void apply(@NotNull List<? extends ApplyPatchChange> changes) {
      for (int i = changes.size() - 1; i >= 0; i--) {
        replaceChange(changes.get(i));
      }
    }
  }

  private final class IgnoreSelectedChangesAction extends ApplySelectedChangesActionBase {
    private IgnoreSelectedChangesAction() {
      getTemplatePresentation().setText(VcsBundle.messagePointer("action.presentation.IgnoreSelectedChangesAction.text"));
      getTemplatePresentation().setIcon(AllIcons.Diff.Remove);
      setShortcutSet(new CompositeShortcutSet(ActionManager.getInstance().getAction("Diff.IgnoreRightSide").getShortcutSet(),
                                              ActionManager.getInstance().getAction("Diff.ApplyLeftSide").getShortcutSet()));
    }

    @Override
    protected boolean isEnabled(@NotNull ApplyPatchChange change) {
      return !change.isResolved();
    }

    @Override
    protected void apply(@NotNull List<? extends ApplyPatchChange> changes) {
      for (ApplyPatchChange change : changes) {
        markChangeResolved(change);
      }
    }
  }

  private abstract class ApplySelectedChangesActionBase extends DumbAwareAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (DiffUtil.isFromShortcut(e)) {
        // consume shortcut even if there are nothing to do - avoid calling some other action
        e.getPresentation().setEnabledAndVisible(true);
        return;
      }

      Presentation presentation = e.getPresentation();
      Editor editor = e.getData(CommonDataKeys.EDITOR);

      Side side = Side.fromValue(Arrays.asList(myResultEditor, myPatchEditor), editor);
      if (side == null) {
        presentation.setEnabledAndVisible(false);
        return;
      }

      presentation.setVisible(true);
      presentation.setEnabled(isSomeChangeSelected(side));
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      final Side side = Side.fromValue(Arrays.asList(myResultEditor, myPatchEditor), editor);
      if (editor == null || side == null) return;

      final List<ApplyPatchChange> selectedChanges = getSelectedChanges(side);
      if (selectedChanges.isEmpty()) return;

      String title = VcsBundle.message("patch.apply.changes.in.patch.resolve", e.getPresentation().getText());

      executeCommand(title, () -> apply(selectedChanges));
    }

    private boolean isSomeChangeSelected(@NotNull Side side) {
      EditorEx editor = side.select(myResultEditor, myPatchEditor);
      return DiffUtil.isSomeRangeSelected(editor, lines -> {
        return ContainerUtil.exists(myModelChanges, change -> isChangeSelected(change, lines, side));
      });
    }

    @NotNull
    @RequiresEdt
    private List<ApplyPatchChange> getSelectedChanges(@NotNull Side side) {
      EditorEx editor = side.select(myResultEditor, myPatchEditor);
      BitSet lines = DiffUtil.getSelectedLines(editor);
      return ContainerUtil.filter(myModelChanges, change -> isChangeSelected(change, lines, side));
    }

    private boolean isChangeSelected(@NotNull ApplyPatchChange change, @NotNull BitSet lines, @NotNull Side side) {
      if (!isEnabled(change)) return false;
      LineRange range = side.select(change.getResultRange(), change.getPatchRange());
      if (range == null) return false;

      return DiffUtil.isSelectedByLine(lines, range.start, range.end);
    }

    protected abstract boolean isEnabled(@NotNull ApplyPatchChange change);

    @RequiresWriteLock
    protected abstract void apply(@NotNull List<? extends ApplyPatchChange> changes);
  }

  private class ApplyNonConflictsAction extends DumbAwareAction {
    ApplyNonConflictsAction() {
      ActionUtil.copyFrom(this, "Diff.ApplyNonConflicts"); //NON-NLS
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      boolean enabled = ContainerUtil.exists(myModelChanges, c -> {
        if (c.isResolved()) return false;
        if (c.getStatus() == AppliedTextPatch.HunkStatus.NOT_APPLIED) return false;
        return true;
      });
      e.getPresentation().setEnabled(enabled);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      List<ApplyPatchChange> changes = myModelChanges;
      if (changes.isEmpty()) return;

      executeCommand(DiffBundle.message("merge.dialog.apply.non.conflicted.changes.command"), () -> {
        for (int i = changes.size() - 1; i >= 0; i--) {
          ApplyPatchChange change = changes.get(i);
          switch (change.getStatus()) {
            case ALREADY_APPLIED -> markChangeResolved(change);
            case EXACTLY_APPLIED -> replaceChange(change);
            case NOT_APPLIED -> {
            }
          }
        }
      });
    }
  }

  //
  // Actions
  //

  private class MyFocusOppositePaneAction extends FocusOppositePaneAction {
    MyFocusOppositePaneAction() {
      super(false);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      EditorEx targetEditor = getCurrentSide().other().select(myResultEditor, myPatchEditor);
      DiffUtil.requestFocus(myProject, targetEditor.getContentComponent());
    }
  }

  private class MyToggleExpandByDefaultAction extends TextDiffViewerUtil.ToggleExpandByDefaultAction {
    MyToggleExpandByDefaultAction() {
      super(getTextSettings(), myFoldingModel);
    }
  }

  private class ShowDiffWithLocalAction extends DumbAwareAction {
    ShowDiffWithLocalAction() {
      super(VcsBundle.messagePointer("action.DumbAwareAction.text.compare.with.local.content"), AllIcons.Actions.Diff);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      DocumentContent resultContent = myPatchRequest.getResultContent();
      DocumentContent localContent = DiffContentFactoryEx.getInstanceEx()
        .documentContent(myProject, true)
        .contextByReferent(resultContent)
        .buildFromText(myPatchRequest.getLocalContent(), false);

      SimpleDiffRequest request = new SimpleDiffRequest(myPatchRequest.getTitle(),
                                                        localContent, resultContent,
                                                        myPatchRequest.getLocalTitle(), myPatchRequest.getResultTitle());

      LogicalPosition currentPosition = DiffUtil.getCaretPosition(myResultEditor);
      request.putUserData(DiffUserDataKeys.SCROLL_TO_LINE, Pair.create(Side.RIGHT, currentPosition.line));

      DiffManager.getInstance().showDiff(myProject, request, new DiffDialogHints(null, myPanel));
    }
  }

  //
  // Helpers
  //

  private class MyPrevNextDifferenceIterable extends PrevNextDifferenceIterableBase<ApplyPatchChange> {
    @NotNull
    @Override
    protected List<ApplyPatchChange> getChanges() {
      return getCurrentSide().select(myResultChanges, myPatchChanges);
    }

    @NotNull
    @Override
    protected EditorEx getEditor() {
      return getCurrentSide().select(myResultEditor, myPatchEditor);
    }

    @Override
    protected int getStartLine(@NotNull ApplyPatchChange change) {
      //noinspection ConstantConditions
      return getCurrentSide().select(change.getResultRange(), change.getPatchAffectedRange()).start;
    }

    @Override
    protected int getEndLine(@NotNull ApplyPatchChange change) {
      //noinspection ConstantConditions
      return getCurrentSide().select(change.getResultRange(), change.getPatchAffectedRange()).end;
    }

    @Override
    protected void scrollToChange(@NotNull ApplyPatchChange change) {
      ApplyPatchViewer.this.scrollToChange(change, getCurrentSide(), true);
    }
  }

  private class MyDividerPainter implements DiffSplitter.Painter, DiffDividerDrawUtil.DividerPaintable {
    @Override
    public void paint(@NotNull Graphics g, @NotNull JComponent divider) {
      Graphics2D gg = DiffDividerDrawUtil.getDividerGraphics(g, divider, myPatchEditor.getComponent());

      gg.setColor(DiffDrawUtil.getDividerColor(myPatchEditor));
      gg.fill(gg.getClipBounds());

      DiffDividerDrawUtil.paintPolygons(gg, divider.getWidth(), myResultEditor, myPatchEditor, this);

      gg.dispose();
    }

    @Override
    public void process(@NotNull Handler handler) {
      for (ApplyPatchChange change : myResultChanges) {
        LineRange resultRange = change.getResultRange();
        LineRange patchRange = change.getPatchRange();
        assert resultRange != null;

        // do not abort - ranges are ordered in patch order, but they can be not ordered in terms of resultRange
        handler.processResolvable(resultRange.start, resultRange.end, patchRange.start, patchRange.end,
                                  change.getDiffType(), change.isResolved());
      }
    }
  }

  private static class MyFoldingModel extends FoldingModelSupport {
    MyFoldingModel(@Nullable Project project, @NotNull EditorEx editor, @NotNull Disposable disposable) {
      super(project, new EditorEx[]{editor}, disposable);
    }

    public void install(@Nullable List<ApplyPatchChange> changes,
                        @NotNull FoldingModelSupport.Settings settings) {
      //noinspection ConstantConditions
      Iterator<int[]> it = map(changes, fragment -> new int[]{
        fragment.getResultRange().start,
        fragment.getResultRange().end
      });
      install(it, null, settings);
    }
  }

  private class MyStatusPanel extends StatusPanel {
    @Nullable
    @Override
    protected String getMessage() {
      int totalUnresolved = 0;
      int alreadyApplied = 0;
      int notApplied = 0;
      for (ApplyPatchChange change : myPatchChanges) {
        if (change.isResolved()) continue;

        totalUnresolved++;
        switch (change.getStatus()) {
          case ALREADY_APPLIED -> alreadyApplied++;
          case NOT_APPLIED -> notApplied++;
          case EXACTLY_APPLIED -> {
          }
        }
      }

      if (totalUnresolved == 0) {
        return DiffBundle.message("apply.somehow.status.message.all.applied", notApplied);
      }
      if (totalUnresolved == notApplied) {
        return DiffBundle.message("apply.somehow.status.message.cant.apply", notApplied);
      }
      else {
        String message = DiffBundle.message("apply.somehow.status.message.cant.apply.some", notApplied, totalUnresolved);
        if (alreadyApplied == 0) return message;
        return message + ". " + DiffBundle.message("apply.somehow.status.message.already.applied", alreadyApplied);
      }
    }
  }
}
