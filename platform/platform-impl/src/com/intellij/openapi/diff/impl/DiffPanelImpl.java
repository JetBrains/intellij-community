// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.EditSourceAction;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.diff.actions.MergeActionGroup;
import com.intellij.openapi.diff.actions.ToggleAutoScrollAction;
import com.intellij.openapi.diff.ex.DiffPanelEx;
import com.intellij.openapi.diff.ex.DiffPanelOptions;
import com.intellij.openapi.diff.impl.external.DiffManagerImpl;
import com.intellij.openapi.diff.impl.fragments.FragmentList;
import com.intellij.openapi.diff.impl.highlighting.DiffPanelState;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.processing.HighlightMode;
import com.intellij.openapi.diff.impl.processing.HorizontalDiffSplitter;
import com.intellij.openapi.diff.impl.settings.DiffMergeEditorSetting;
import com.intellij.openapi.diff.impl.settings.DiffMergeSettings;
import com.intellij.openapi.diff.impl.settings.DiffMergeSettingsAction;
import com.intellij.openapi.diff.impl.settings.DiffToolSettings;
import com.intellij.openapi.diff.impl.splitter.DiffDividerPaint;
import com.intellij.openapi.diff.impl.splitter.LineBlocks;
import com.intellij.openapi.diff.impl.util.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.pom.Navigatable;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.JBColor;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.LineSeparator;
import com.intellij.util.diff.FilesTooBigForDiffException;
import com.intellij.util.ui.PlatformColors;
import gnu.trove.TIntFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseListener;
import java.util.Arrays;

import static com.intellij.openapi.wm.IdeFocusManager.getGlobalInstance;

public class DiffPanelImpl implements DiffPanelEx, ContentChangeListener, TwoSidesContainer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.DiffPanelImpl");

  private final DiffSplitterI mySplitter;
  private final DiffPanelOuterComponent myPanel;

  private final Window myOwnerWindow;
  private final DiffPanelOptions myOptions;

  private final DiffPanelState myData;

  private final Rediffers myDiffUpdater;
  private final DiffSideView myLeftSide;
  private final DiffSideView myRightSide;
  private DiffSideView myCurrentSide;
  private LineBlocks myLineBlocks = LineBlocks.EMPTY;
  private final SyncScrollSupport myScrollSupport = new SyncScrollSupport();
  private final FontSizeSynchronizer myFontSizeSynchronizer = new FontSizeSynchronizer();
  private DiffRequest myDiffRequest;
  private boolean myIsRequestFocus = true;
  private boolean myIsSyncScroll;

  private DiffRequest.ToolbarAddons createToolbar() {
    return new DiffRequest.ToolbarAddons() {
      @Override
      public void customize(DiffToolbar toolbar) {
        ActionManager actionManager = ActionManager.getInstance();
        toolbar.addAction(actionManager.getAction("DiffPanel.Toolbar"));
        toolbar.addSeparator();
        toolbar.addAction(new ToggleAutoScrollAction());
        toolbar.addSeparator();
        toolbar.addAction(actionManager.getAction("ContextHelp"));
        toolbar.addAction(getEditSourceAction());
        toolbar.addSeparator();
        toolbar.addAction(new DiffMergeSettingsAction(Arrays.asList(getEditor1(), getEditor2()),
                                                      ServiceManager.getService(myProject, DiffToolSettings.class)));
      }

      @NotNull
      private AnAction getEditSourceAction() {
        AnAction editSourceAction = new EditSourceAction();
        editSourceAction.getTemplatePresentation().setIcon(AllIcons.Actions.EditSource);
        editSourceAction.getTemplatePresentation().setText(ActionsBundle.actionText("EditSource"));
        editSourceAction.getTemplatePresentation().setDescription(ActionsBundle.actionText("EditSource"));
        editSourceAction.registerCustomShortcutSet(CommonShortcuts.getEditSource(), myPanel, DiffPanelImpl.this);
        return editSourceAction;
      }
    };
  }

  private boolean myDisposed = false;
  @NotNull private final Project myProject;
  private final boolean myIsHorizontal;
  private final DiffTool myParentTool;
  private EditorNotificationPanel myTopMessageDiffPanel;
  private final VisibleAreaListener myVisibleAreaListener;
  private final int myDiffDividerPolygonsOffset;

  public DiffPanelImpl(final Window owner,
                       @NotNull Project project,
                       boolean enableToolbar,
                       boolean horizontal,
                       int diffDividerPolygonsOffset,
                       DiffTool parentTool) {
    DiffUsageTriggerCollector.trigger("deprecated.DiffPanelImpl");

    myProject = project;
    myIsHorizontal = horizontal;
    myParentTool = parentTool;
    myOptions = new DiffPanelOptions(this);
    myPanel = new DiffPanelOuterComponent(TextDiffType.DIFF_TYPES, null);
    myPanel.disableToolbar(!enableToolbar);
    if (enableToolbar) myPanel.resetToolbar();
    myOwnerWindow = owner;
    myIsSyncScroll = true;
    final boolean v = !horizontal;
    myLeftSide =  new DiffSideView(this, new CustomLineBorder(1, 0, v ? 0 : 1, v ? 0 : 1));
    myRightSide = new DiffSideView(this, new CustomLineBorder(v ? 0 : 1, v ? 0 : 1, 1, 0));
    myLeftSide.becomeMaster();
    myDiffUpdater = new Rediffers(this);

    myDiffDividerPolygonsOffset = diffDividerPolygonsOffset;

    myData = createDiffPanelState(this);

    if (horizontal) {
      mySplitter = new DiffSplitter(myLeftSide.getComponent(), myRightSide.getComponent(),
                                    new DiffDividerPaint(this, FragmentSide.SIDE1, diffDividerPolygonsOffset), myData);
    }
    else {
      mySplitter = new HorizontalDiffSplitter(myLeftSide.getComponent(), myRightSide.getComponent());
    }

    myPanel.insertDiffComponent(mySplitter.getComponent(), new MyScrollingPanel());
    myPanel.setDataProvider(new MyGenericDataProvider(this));

    ComparisonPolicy comparisonPolicy = getComparisonPolicy();
    ComparisonPolicy defaultComparisonPolicy = DiffManagerImpl.getInstanceEx().getComparisonPolicy();
    if (comparisonPolicy != defaultComparisonPolicy) {
      setComparisonPolicy(defaultComparisonPolicy, false);
    }

    HighlightMode highlightMode = getHighlightMode();
    HighlightMode defaultHighlightMode = DiffManagerImpl.getInstanceEx().getHighlightMode();
    if (highlightMode != defaultHighlightMode) {
      setHighlightMode(defaultHighlightMode, false);
    }

    myVisibleAreaListener = new VisibleAreaListener() {
      @Override
      public void visibleAreaChanged(@NotNull VisibleAreaEvent e) {
        Editor editor1 = getEditor1();
        if (editor1 != null) {
          editor1.getComponent().repaint();
        }
        Editor editor2 = getEditor2();
        if (editor2 != null) {
          editor2.getComponent().repaint();
        }
      }
    };
    registerActions();
  }

  private void registerActions() {
    //control+tab switches editors
    DumbAwareAction.create(e -> {
      if (getEditor1() != null && getEditor2() != null) {
        Editor focus = getEditor1().getContentComponent().hasFocus() ? getEditor2() : getEditor1();
        IdeFocusManager.getGlobalInstance().requestFocus(focus.getContentComponent(), true);
        focus.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
      }
    }).registerCustomShortcutSet(CustomShortcutSet.fromString("control TAB"), myPanel, this);
  }

  protected DiffPanelState createDiffPanelState(@NotNull Disposable parentDisposable) {
    return new DiffPanelState(this, myProject, getDiffDividerPolygonsOffset(), parentDisposable);
  }

  public int getDiffDividerPolygonsOffset() {
    return myDiffDividerPolygonsOffset;
  }

  public boolean isHorisontal() {
    return myIsHorizontal;
  }

  public DiffPanelState getDiffPanelState() {
    return myData;
  }

  public void noSynchScroll() {
    myIsSyncScroll = false;
  }

  public DiffSplitterI getSplitter() {
    return mySplitter;
  }

  public void reset() {
    //myUi.getContentManager().removeAllContents(false);
    myPanel.setPreferredHeightGetter(null);
  }

  public void prefferedSizeByContents(final int maximumLines) {
    if (getEditor1() == null && getEditor2() == null) return;

    if (getEditor1() != null) {
      getEditor1().getSettings().setAdditionalLinesCount(0);
      getEditor1().getSettings().setAdditionalPageAtBottom(false);
    }
    if (getEditor2() != null) {
      getEditor2().getSettings().setAdditionalLinesCount(0);
      getEditor2().getSettings().setAdditionalPageAtBottom(false);
    }
    myPanel.setPrefferedWidth(20);
    myPanel.setPreferredHeightGetter(() -> {
      final int size1 = getEditor1() == null ? 10 : getEditor1().getComponent().getPreferredSize().height;
      final int size2 = getEditor2() == null ? 10 : getEditor2().getComponent().getPreferredSize().height;
      final int lineHeight = getEditor1() == null ? getEditor2().getLineHeight() : getEditor1().getLineHeight();
      final int size = Math.max(size1, size2);
      if (maximumLines > 0) {
        return Math.min(size, maximumLines * lineHeight);
      }
      return size;
    });
  }

  @Override
  @Nullable
  public Editor getEditor1() {
    return myLeftSide.getEditor();
  }

  @Override
  @Nullable
  public Editor getEditor2() {
    if (myDisposed) LOG.error("Disposed");
    Editor editor = myRightSide.getEditor();
    if (editor != null) return editor;
    if (myData.getContent2() == null) LOG.error("No content 2");
    return editor;
  }

  @Override
  public void setContents(DiffContent content1, DiffContent content2) {
    LOG.assertTrue(content1 != null && content2 != null);
    LOG.assertTrue(!myDisposed);
    myData.setContents(content1, content2);
    Project project = myData.getProject();
    FileType[] types = DiffUtil.chooseContentTypes(new DiffContent[]{content1, content2});
    VirtualFile beforeFile = content1.getFile();
    VirtualFile afterFile = content2.getFile();
    String path = myDiffRequest == null ? null : myDiffRequest.getWindowTitle();
    myLeftSide.setHighlighterFactory(createHighlighter(types[0], beforeFile, afterFile, path, project));
    myRightSide.setHighlighterFactory(createHighlighter(types[1], afterFile, beforeFile, path, project));
    setSplitterProportion(content1, content2);
    rediff();
    if (myIsRequestFocus) {
      myPanel.requestScrollEditors();
    }
  }

  private void setSplitterProportion(DiffContent content1, DiffContent content2) {
    if (content1.isEmpty()) {
      mySplitter.setProportion(0f);
      mySplitter.setResizeEnabled(false);
      return;
    }
    if (content2.isEmpty()) {
      mySplitter.setProportion(1.0f);
      mySplitter.setResizeEnabled(false);
      return;
    }
    mySplitter.setProportion(0.5f);
    mySplitter.setResizeEnabled(true);
  }

  @Override
  public void removeStatusBar() {
    myPanel.removeStatusBar();
  }

  @Override
  public void enableToolbar(final boolean value) {
    myPanel.disableToolbar(!value);
  }

  public void setLineNumberConvertors(@NotNull TIntFunction old, @NotNull TIntFunction newConvertor) {
    if (getEditor1() != null) {
      ((EditorGutterComponentEx) getEditor1().getGutter()).setLineNumberConvertor(old);
    }
    if (getEditor2() != null) {
      ((EditorGutterComponentEx) getEditor2().getGutter()).setLineNumberConvertor(newConvertor);
    }
  }
  // todo pay attention here
  private static DiffHighlighterFactory createHighlighter(FileType contentType,
                                                          VirtualFile file,
                                                          VirtualFile otherFile,
                                                          String path,
                                                          Project project) {
    VirtualFile baseFile = file;
    if (baseFile == null) baseFile = otherFile;
    if (baseFile == null && path != null) baseFile = LocalFileSystem.getInstance().findFileByPath(path);

    return new DiffHighlighterFactoryImpl(contentType, baseFile, project);
  }

  void rediff() {
    try {
      if (myTopMessageDiffPanel != null) {
        myPanel.removeTopComponent(myTopMessageDiffPanel);
      }
      LineBlocks blocks = myData.updateEditors();
      setLineBlocks(blocks != null ? blocks : LineBlocks.EMPTY);
      if (blocks != null && blocks.getCount() == 0) {
        if (myData.isContentsEqual()) {
          setFileContentsAreIdentical();
        }
      }
    }
    catch (FilesTooBigForDiffException e) {
      setTooBigFileErrorContents();
    }
  }

  @Override
  public void setTooBigFileErrorContents() {
    setLineBlocks(LineBlocks.EMPTY);
    myTopMessageDiffPanel = new CanNotCalculateDiffPanel();
    myPanel.insertTopComponent(myTopMessageDiffPanel);
  }

  @Override
  public void setPatchAppliedApproximately() {
    if (!(myTopMessageDiffPanel instanceof CanNotCalculateDiffPanel)) {
      myTopMessageDiffPanel = new DiffIsApproximate();
      myPanel.insertTopComponent(myTopMessageDiffPanel);
    }
  }

  public void setFileContentsAreIdentical() {
    if (myTopMessageDiffPanel == null || myTopMessageDiffPanel instanceof FileContentsAreIdenticalDiffPanel) {
      LineSeparator sep1 = myData.getContent1() == null ? null : myData.getContent1().getLineSeparator();
      LineSeparator sep2 = myData.getContent2() == null ? null : myData.getContent2().getLineSeparator();

      if (LineSeparator.knownAndDifferent(sep1, sep2)) {
        myTopMessageDiffPanel = new LineSeparatorsOnlyDiffPanel();
      }
      else {
        myTopMessageDiffPanel = new FileContentsAreIdenticalDiffPanel();
      }
      myPanel.insertTopComponent(myTopMessageDiffPanel);
    }
  }

  @Override
  public void setTitle1(String title) {
    setTitle(title, true);
  }

  private void setTitle(String title, boolean left) {
    Editor editor = left ? getEditor1() : getEditor2();
    if (editor == null) return;
    title = addReadOnly(title, editor);
    JLabel label = new JLabel(title);
    if (left) {
      myLeftSide.setTitle(label);
    }
    else {
      myRightSide.setTitle(label);
    }
  }

  @Nullable
  private static String addReadOnly(@Nullable String title, @Nullable Editor editor) {
    if (editor == null || title == null) {
      return title;
    }
    boolean readonly = editor.isViewer() || !editor.getDocument().isWritable();
    if (readonly) {
      title += " " + DiffBundle.message("diff.content.read.only.content.title.suffix");
    }
    return title;
  }

  @Override
  public void setTitle2(String title) {
    setTitle(title, false);
  }

  private void setLineBlocks(@NotNull LineBlocks blocks) {
    myLineBlocks = blocks;
    mySplitter.redrawDiffs();
    updateStatusBar();
  }

  public void invalidateDiff() {
    setLineBlocks(LineBlocks.EMPTY);
    myData.removeActions();
  }

  public FragmentList getFragments() {
    return myData.getFragmentList();
  }

  private int[] getFragmentBeginnings() {
    return getFragmentBeginnings(myCurrentSide.getSide());
  }

  int[] getFragmentBeginnings(FragmentSide side) {
    return getLineBlocks().getBeginnings(side);
  }

  @Override
  public void dispose() {
    myDisposed = true;
    myDiffUpdater.dispose();
    Disposer.dispose(myScrollSupport);
    Disposer.dispose(myData);
    myPanel.cancelScrollEditors();
    myPanel.setDataProvider(null);
    myPanel.setScrollingPanel(null);
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  private void updateStatusBar() {
    myPanel.setStatusBarText(getNumDifferencesText());
  }

  public String getNumDifferencesText() {
    return DiffBundle.message("diff.count.differences.status.text", getLineBlocks().getCount());
  }

  @Override
  public boolean hasDifferences() {
    return getLineBlocks().getCount() > 0 || myTopMessageDiffPanel != null;
  }

  @Override
  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return myCurrentSide.getFocusableComponent();
  }

  @Override
  public boolean acceptsType(DiffViewerType type) {
    return DiffViewerType.contents.equals(type);
  }

  @Override
  public ComparisonPolicy getComparisonPolicy() {
    return myData.getComparisonPolicy();
  }

  @Override
  public void setComparisonPolicy(@NotNull ComparisonPolicy comparisonPolicy) {
    setComparisonPolicy(comparisonPolicy, true);
  }

  private void setComparisonPolicy(@NotNull ComparisonPolicy policy, boolean notifyManager) {
    myData.setComparisonPolicy(policy);
    rediff();

    if (notifyManager) {
      DiffManagerImpl.getInstanceEx().setComparisonPolicy(policy);
    }
  }

  @Override
  @NotNull
  public HighlightMode getHighlightMode() {
    return myData.getHighlightMode();
  }

  @Override
  public void setHighlightMode(@NotNull HighlightMode mode) {
    setHighlightMode(mode, true);
  }

  public void setHighlightMode(@NotNull HighlightMode mode, boolean notifyManager) {
    myData.setHighlightMode(mode);
    rediff();

    if (notifyManager) {
      DiffManagerImpl.getInstanceEx().setHighlightMode(mode);
    }
  }

  @Override
  public void setAutoScrollEnabled(boolean enabled) {
    myScrollSupport.setEnabled(enabled);
  }

  @Override
  public boolean isAutoScrollEnabled() {
    return myScrollSupport.isEnabled();
  }

  public Rediffers getDiffUpdater() {
    return myDiffUpdater;
  }

  @Override
  public void onContentChangedIn(@NotNull EditorSource source) {
    myDiffUpdater.contentRemoved(source);
    final EditorEx editor = source.getEditor();
    if (myIsHorizontal && source.getSide() == FragmentSide.SIDE1 && editor != null) {
      editor.setVerticalScrollbarOrientation(EditorEx.VERTICAL_SCROLLBAR_LEFT);
    }
    DiffSideView viewSide = getSideView(source.getSide());
    viewSide.setEditorSource(getProject(), source);
    Disposer.dispose(myScrollSupport);
    if (editor == null) {
      if (!myDisposed) {
        rediff();
      }
      return;
    }

    final MouseListener mouseListener = PopupHandler
      .installUnknownPopupHandler(editor.getContentComponent(), new MergeActionGroup(this, source.getSide()), ActionManager.getInstance());
    myDiffUpdater.contentAdded(source);
    editor.getSettings().setLineNumbersShown(true);
    editor.getSettings().setFoldingOutlineShown(false);
    editor.getFoldingModel().setFoldingEnabled(false);
    ((EditorMarkupModel)editor.getMarkupModel()).setErrorStripeVisible(true);

    Editor editor1 = getEditor(FragmentSide.SIDE1);
    Editor editor2 = getEditor(FragmentSide.SIDE2);
    if (editor1 != null && editor2 != null && myIsSyncScroll) {
      myScrollSupport.install(new EditingSides[]{this});
    }

    final VisibleAreaListener visibleAreaListener = mySplitter.getVisibleAreaListener();
    final ScrollingModel scrollingModel = editor.getScrollingModel();
    if (visibleAreaListener != null) {
      scrollingModel.addVisibleAreaListener(visibleAreaListener);
      scrollingModel.addVisibleAreaListener(myVisibleAreaListener);
    }
    myFontSizeSynchronizer.synchronize(editor);
    source.addDisposable(new Disposable() {
      @Override
      public void dispose() {
        myFontSizeSynchronizer.stopSynchronize(editor);
      }
    });
    source.addDisposable(new Disposable() {
      @Override
      public void dispose() {
        if (visibleAreaListener != null) {
          scrollingModel.removeVisibleAreaListener(visibleAreaListener);
          scrollingModel.removeVisibleAreaListener(myVisibleAreaListener);
        }
        editor.getContentComponent().removeMouseListener(mouseListener);
      }
    });
  }

  @Override
  public void setCurrentSide(@NotNull DiffSideView viewSide) {
    LOG.assertTrue(viewSide != myCurrentSide);
    if (myCurrentSide != null) myCurrentSide.beSlave();
    myCurrentSide = viewSide;
  }

  public DiffSideView getCurrentSide() { return myCurrentSide; }

  @Override
  public Project getProject() {
    return myData.getProject();
  }

  @Override
  public void showSource(@Nullable Navigatable descriptor) {
    myOptions.showSource(descriptor);
  }

  @Override
  public DiffPanelOptions getOptions() {
    return myOptions;
  }

  @Override
  public Editor getEditor(FragmentSide side) {
    return getSideView(side).getEditor();
  }

  public DiffSideView getSideView(FragmentSide side) {
    if (side == FragmentSide.SIDE1) {
      return myLeftSide;
    }
    if (side == FragmentSide.SIDE2) return myRightSide;
    throw new IllegalArgumentException(String.valueOf(side));
  }

  @Override
  public LineBlocks getLineBlocks() { return myLineBlocks; }

  static JComponent createComponentForTitle(@Nullable String title,
                                            @Nullable final LineSeparator sep1,
                                            @Nullable final LineSeparator sep2,
                                            boolean left) {
    if (sep1 != null && sep2 != null && !sep1.equals(sep2)) {
      LineSeparator separator = left ? sep1 : sep2;
      JPanel bottomPanel = new JPanel(new BorderLayout());
      JLabel sepLabel = new JLabel(separator.name());
      sepLabel.setForeground(separator.equals(LineSeparator.CRLF) ? JBColor.RED : PlatformColors.BLUE);
      bottomPanel.add(sepLabel, left ? BorderLayout.EAST : BorderLayout.WEST);

      JPanel panel = new JPanel(new BorderLayout());
      panel.add(new JLabel(title == null ? "" : title));
      panel.add(bottomPanel, BorderLayout.SOUTH);
      return panel;
    }
    else {
      return new JBLabel(title == null ? "" : title);
    }
  }

  @Override
  public boolean canShowRequest(DiffRequest request) {
    return myParentTool != null && myParentTool.canShow(request);
  }

  @Override
  public void setDiffRequest(DiffRequest data) {
    myDiffRequest = data;
    if (data.getHints().contains(DiffTool.HINT_DO_NOT_IGNORE_WHITESPACES)) {
      setComparisonPolicy(ComparisonPolicy.DEFAULT, false);
    }

    DiffContent content1 = data.getContents()[0];
    DiffContent content2 = data.getContents()[1];

    setContents(content1, content2);
    setTitles(data);

    setWindowTitle(myOwnerWindow, data.getWindowTitle());
    myPanel.setToolbarActions(createToolbar());
    myPanel.resetToolbar();
    myPanel.registerToolbarActions();
    initEditorSettings(getEditor1());
    initEditorSettings(getEditor2());


    if (myIsRequestFocus) {
      IdeFocusManager fm = IdeFocusManager.getInstance(myProject);
      boolean isEditor1Focused = getEditor1() != null
                                 && fm.getFocusedDescendantFor(getEditor1().getComponent()) != null;

      boolean isEditor2Focused = myData.getContent2() != null
                                 && getEditor2() != null
                                 && fm.getFocusedDescendantFor(getEditor2().getComponent()) != null;

      if (isEditor1Focused || isEditor2Focused) {
        Editor e = isEditor2Focused ? getEditor2() : getEditor1();
        if (e != null) {
          fm.requestFocus(e.getContentComponent(), true);
        }
      }

      myPanel.requestScrollEditors();
    }
  }

  private static void initEditorSettings(@Nullable Editor editor) {
    if (editor == null) {
      return;
    }
    Project project = editor.getProject();
    DiffMergeSettings settings = project == null ? null : ServiceManager.getService(project, DiffToolSettings.class);
    for (DiffMergeEditorSetting property : DiffMergeEditorSetting.values()) {
      property.apply(editor, settings == null ? property.getDefault() : settings.getPreference(property));
    }
    ((EditorEx)editor).getGutterComponentEx().setShowDefaultGutterPopup(false);
  }

  private void setTitles(@NotNull DiffRequest data) {
    LineSeparator sep1 = data.getContents()[0].getLineSeparator();
    LineSeparator sep2 = data.getContents()[1].getLineSeparator();

    String title1 = addReadOnly(data.getContentTitles()[0], myLeftSide.getEditor());
    String title2 = addReadOnly(data.getContentTitles()[1], myRightSide.getEditor());

    setTitle1(createComponentForTitle(title1, sep1, sep2, true));
    setTitle2(createComponentForTitle(title2, sep1, sep2, false));
  }

  private void setTitle1(JComponent title) {
    myLeftSide.setTitle(title);
  }

  private void setTitle2(JComponent title) {
    myRightSide.setTitle(title);
  }

  private static void setWindowTitle(Window window, String title) {
    if (window instanceof JDialog) {
      ((JDialog)window).setTitle(title);
    }
    else if (window instanceof JFrame) ((JFrame)window).setTitle(title);
  }

  @Nullable
  public static DiffPanelImpl fromDataContext(DataContext dataContext) {
    DiffViewer viewer = PlatformDataKeys.DIFF_VIEWER.getData(dataContext);
    return viewer instanceof DiffPanelImpl ? (DiffPanelImpl)viewer : null;
  }

  public Window getOwnerWindow() {
    return myOwnerWindow;
  }

  public void focusOppositeSide() {
    if (myCurrentSide == myLeftSide) {
      getGlobalInstance().doWhenFocusSettlesDown(() -> {
        getGlobalInstance().requestFocus(myRightSide.getEditor().getContentComponent(), true);
      });
    }
    else {
      getGlobalInstance().doWhenFocusSettlesDown(() -> {
        getGlobalInstance().requestFocus(myLeftSide.getEditor().getContentComponent(), true);
      });
    }
  }

  @Override
  public void setRequestFocus(boolean isRequestFocus) {
    myIsRequestFocus = isRequestFocus;
  }

  private class MyScrollingPanel implements DiffPanelOuterComponent.ScrollingPanel {

    @Override
    public void scrollEditors() {
      getOptions().onNewContent(myCurrentSide);
      scrollCurrentToFirstDiff();
    }

    private void scrollCurrentToFirstDiff() {
      int[] fragments = getFragmentBeginnings();
      if (fragments.length > 0) myCurrentSide.scrollToFirstDiff(fragments[0]);
    }
  }

  private class MyGenericDataProvider extends GenericDataProvider {
    private final DiffPanelImpl myDiffPanel;

    private MyGenericDataProvider(DiffPanelImpl diffPanel) {
      myDiffPanel = diffPanel;
    }

    private final FocusDiffSide myFocusDiffSide = new FocusDiffSide() {
      @Override
      public Editor getEditor() {
        return myDiffPanel.getCurrentSide().getEditor();
      }

      @Override
      public int[] getFragmentStartingLines() {
        return myDiffPanel.getFragmentBeginnings();
      }
    };

    @Override
    public Object getData(@NotNull String dataId) {
      if (PlatformDataKeys.DIFF_VIEWER.is(dataId)) {
        return myDiffPanel;
      }
      if (FocusDiffSide.DATA_KEY.is(dataId)) {
        return myDiffPanel.myCurrentSide == null ? null : myFocusDiffSide;
      }
      if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
        final DiffSideView currentSide = myDiffPanel.myCurrentSide;
        if (currentSide != null) {
          return new DiffNavigatable(currentSide);
        }
      }

      return super.getData(dataId);
    }
  }

  public static class FileContentsAreIdenticalDiffPanel extends EditorNotificationPanel {
    public FileContentsAreIdenticalDiffPanel() {
      myLabel.setText(DiffBundle.message("diff.contents.are.identical.message.text"));
    }
  }

  public static class LineSeparatorsOnlyDiffPanel extends FileContentsAreIdenticalDiffPanel {
    public LineSeparatorsOnlyDiffPanel() {
      myLabel.setText(DiffBundle.message("diff.contents.have.differences.only.in.line.separators.message.text"));
    }
  }

  public static class CanNotCalculateDiffPanel extends EditorNotificationPanel {
    public CanNotCalculateDiffPanel() {
      myLabel.setText("Can not calculate diff. File is too big and there are too many changes.");
    }
  }

  public static class DiffIsApproximate extends EditorNotificationPanel {
    public DiffIsApproximate() {
      myLabel.setText("<html>Couldn't find context for patch. Some fragments were applied at the best possible place. <b>Please check carefully.</b></html>");
    }
  }

  private class DiffNavigatable implements Navigatable {
    private final DiffSideView mySide;

    DiffNavigatable(DiffSideView side) {
      mySide = side;
    }

    @Override
    public boolean canNavigateToSource() {
      return false;
    }

    @Override
    public boolean canNavigate() {
      return true;
    }

    @Override
    public void navigate(boolean requestFocus) {
      showSource(mySide.getCurrentOpenFileDescriptor());
    }
  }
}
