/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.diff.actions.MergeActionGroup;
import com.intellij.openapi.diff.ex.DiffPanelEx;
import com.intellij.openapi.diff.ex.DiffPanelOptions;
import com.intellij.openapi.diff.impl.external.DiffManagerImpl;
import com.intellij.openapi.diff.impl.fragments.FragmentList;
import com.intellij.openapi.diff.impl.highlighting.DiffPanelState;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.splitter.DiffDividerPaint;
import com.intellij.openapi.diff.impl.splitter.LineBlocks;
import com.intellij.openapi.diff.impl.util.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PopupHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.security.InvalidParameterException;

public class DiffPanelImpl implements DiffPanelEx, ContentChangeListener, TwoSidesContainer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.DiffPanelImpl");

  private final DiffSplitter mySplitter;
  private final DiffPanelOutterComponent myPanel;

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

  private final DiffRequest.ToolbarAddons TOOL_BAR = new DiffRequest.ToolbarAddons() {
    public void customize(DiffToolbar toolbar) {
      ActionManager actionManager = ActionManager.getInstance();
      toolbar.addAction(actionManager.getAction("DiffPanel.Toolbar"));
    }
  };
  private boolean myDisposed = false;
  private final GenericDataProvider myDataProvider;

  public DiffPanelImpl(final Window owner, Project project, boolean enableToolbar) {
    myOptions = new DiffPanelOptions(this);
    myPanel = new DiffPanelOutterComponent(TextDiffType.DIFF_TYPES, TOOL_BAR);
    myPanel.disableToolbar(!enableToolbar);
    if (enableToolbar) myPanel.resetToolbar();
    myOwnerWindow = owner;
    myLeftSide = new DiffSideView("", this);
    myRightSide = new DiffSideView("", this);
    myLeftSide.becomeMaster();
    myDiffUpdater = new Rediffers(this);

    myData = new DiffPanelState(this, project);
    mySplitter = new DiffSplitter(myLeftSide.getComponent(), myRightSide.getComponent(),
                                  new DiffDividerPaint(this, FragmentSide.SIDE1));
    myPanel.insertDiffComponent(mySplitter, new MyScrollingPanel());
    myDataProvider = new MyGenericDataProvider(this);
    myPanel.setDataProvider(myDataProvider);

    final ComparisonPolicy comparisonPolicy = getComparisonPolicy();
    final ComparisonPolicy defaultComparisonPolicy = DiffManagerImpl.getInstanceEx().getComparisonPolicy();

    if (defaultComparisonPolicy != null && comparisonPolicy != defaultComparisonPolicy) {
      setComparisonPolicy(defaultComparisonPolicy);
    }


  }

  public Editor getEditor1() {
    return myLeftSide.getEditor();
  }

  public Editor getEditor2() {
    if (myDisposed) LOG.error("Disposed");
    Editor editor = myRightSide.getEditor();
    if (editor != null) return editor;
    if (myData.getContent2() == null) LOG.error("No content 2");
    return editor;
  }

  public void setContents(DiffContent content1, DiffContent content2) {
    LOG.assertTrue(!myDisposed);
    myData.setContents(content1, content2);
    Project project = myData.getProject();
    FileType[] types = DiffUtil.chooseContentTypes(new DiffContent[]{content1, content2});
    VirtualFile baseFile = content1.getFile();
    if (baseFile == null && myDiffRequest != null) {
      String path = myDiffRequest.getWindowTitle();
      if (path != null) baseFile = LocalFileSystem.getInstance().findFileByPath(path);
    }
    myLeftSide.setHighlighterFactory(createHighlighter(types[0], baseFile, project));
    myRightSide.setHighlighterFactory(createHighlighter(types[1], baseFile, project));
    rediff();
    myPanel.requestScrollEditors();
  }

  private static DiffHighlighterFactory createHighlighter(FileType contentType, VirtualFile file, Project project) {
    return new DiffHighlighterFactoryImpl(contentType, file, project);
  }

  void rediff() {
    setLineBlocks(myData.updateEditors());
  }

  public void setTitle1(String title) {
    myLeftSide.setTitle(title);
  }

  public void setTitle2(String title) {
    myRightSide.setTitle(title);
  }

  private void setLineBlocks(LineBlocks blocks) {
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
    return getLineBlocks().getBegginings(side);
  }

  public void dispose() {
    myDisposed = true;
    myDiffUpdater.dispose();
    Disposer.dispose(myScrollSupport);
    Disposer.dispose(myData);
    myPanel.cancelScrollEditors();
    JComponent component = myPanel.getBottomComponent();
    if (component instanceof Disposable) {
      Disposer.dispose(((Disposable)component));
    }
    myPanel.setBottomComponent(null);
  }

  public JComponent getComponent() {
    return myPanel;
  }

  private void updateStatusBar() {
    int differentLineBlocks = getLineBlocks().getCount();
    myPanel.setStatusBarText(DiffBundle.message("diff.count.differences.status.text", differentLineBlocks));
  }

  public boolean hasDifferences() {
    return getLineBlocks().getCount() > 0;
  }

  public JComponent getPreferredFocusedComponent() {
    return myCurrentSide.getFocusableComponent();
  }

  public int getContentsNumber() {
    return 2;
  }

  public ComparisonPolicy getComparisonPolicy() {
    return myData.getComparisonPolicy();
  }

  private void setComparisonPolicy(ComparisonPolicy policy, boolean notifyManager) {
    myData.setComparisonPolicy(policy);
    rediff();

    if (notifyManager) {
      DiffManagerImpl.getInstanceEx().setComparisonPolicy(policy);
    }
  }

  public void setComparisonPolicy(ComparisonPolicy comparisonPolicy) {
    setComparisonPolicy(comparisonPolicy, true);
  }

  public Rediffers getDiffUpdater() {
    return myDiffUpdater;
  }

  public void onContentChangedIn(EditorSource source) {
    myDiffUpdater.contentRemoved(source);
    final EditorEx editor = source.getEditor();
    if (source.getSide() == FragmentSide.SIDE1 && editor != null) {
      editor.setVerticalScrollbarOrientation(EditorEx.VERTICAL_SCROLLBAR_LEFT);
    }
    DiffSideView viewSide = getSideView(source.getSide());
    viewSide.setEditorSource(source);
    Disposer.dispose(myScrollSupport);
    if (editor == null) {
      if (!myDisposed) {
        rediff();
      }
      return;
    }
    PopupHandler.installUnknownPopupHandler(editor.getContentComponent(), new MergeActionGroup(this, source.getSide()), ActionManager.getInstance());
    myDiffUpdater.contentAdded(source);
    editor.getSettings().setLineNumbersShown(true);
    editor.getSettings().setFoldingOutlineShown(false);
    ((FoldingModelEx)editor.getFoldingModel()).setFoldingEnabled(false);
    ((EditorMarkupModel)editor.getMarkupModel()).setErrorStripeVisible(true);

    Editor editor1 = getEditor(FragmentSide.SIDE1);
    Editor editor2 = getEditor(FragmentSide.SIDE2);
    if (editor1 != null && editor2 != null) {
      myScrollSupport.install(new EditingSides[]{this});
    }

    final VisibleAreaListener visibleAreaListener = mySplitter.getVisibleAreaListener();
    final ScrollingModel scrollingModel = editor.getScrollingModel();
    scrollingModel.addVisibleAreaListener(visibleAreaListener);
    myFontSizeSynchronizer.synchronize(editor);
    source.addDisposable(new Disposable() {
      public void dispose() {
        myFontSizeSynchronizer.stopSynchronize(editor);
      }
    });
    source.addDisposable(new Disposable() {
      public void dispose() {
        scrollingModel.removeVisibleAreaListener(visibleAreaListener);
      }
    });
  }

  public void setCurrentSide(@NotNull DiffSideView viewSide) {
    LOG.assertTrue(viewSide != myCurrentSide);
    if (myCurrentSide != null) myCurrentSide.beSlave();
    myCurrentSide = viewSide;
  }

  private DiffSideView getCurrentSide() { return myCurrentSide; }

  public Project getProject() {
    return myData.getProject();
  }

  public void showSource(OpenFileDescriptor descriptor) {
    myOptions.showSource(descriptor);
  }

  public DiffPanelOptions getOptions() {
    return myOptions;
  }

  public Editor getEditor(FragmentSide side) {
    return getSideView(side).getEditor();
  }

  private DiffSideView getSideView(FragmentSide side) {
    if (side == FragmentSide.SIDE1) {
      return myLeftSide;
    }
    else if (side == FragmentSide.SIDE2) return myRightSide;
    throw new InvalidParameterException(String.valueOf(side));
  }

  public LineBlocks getLineBlocks() { return myLineBlocks; }

  public void setDiffRequest(DiffRequest data) {
    myDiffRequest = data;
    if (data.getHints().contains(DiffTool.HINT_DO_NOT_IGNORE_WHITESPACES)) {
      setComparisonPolicy(ComparisonPolicy.DEFAULT, false);
    }
    myDataProvider.putData(myDiffRequest.getGenericData());

    setContents(data.getContents()[0], data.getContents()[1]);
    setTitle1(data.getContentTitles()[0]);
    setTitle2(data.getContentTitles()[1]);
    setWindowTitle(myOwnerWindow, data.getWindowTitle());
    data.customizeToolbar(myPanel.resetToolbar());
    myPanel.registerToolbarActions();

    final JComponent oldBottomComponent = myPanel.getBottomComponent();
    if (oldBottomComponent instanceof Disposable) {
      Disposer.dispose(((Disposable)oldBottomComponent));
    }
    final JComponent newBottomComponent = data.getBottomComponent();
    myPanel.setBottomComponent(newBottomComponent);
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
      myRightSide.getEditor().getContentComponent().requestFocus();
    }
    else {
      myLeftSide.getEditor().getContentComponent().requestFocus();
    }
  }

  private class MyScrollingPanel implements DiffPanelOutterComponent.ScrollingPanel {

    public void scrollEditors() {
      getOptions().onNewContent(myCurrentSide);
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
      public Editor getEditor() {
        return myDiffPanel.getCurrentSide().getEditor();
      }

      public int[] getFragmentStartingLines() {
        return myDiffPanel.getFragmentBeginnings();
      }
    };

    @Override
    public Object getData(String dataId) {
      if (PlatformDataKeys.DIFF_VIEWER.is(dataId)) {
        return myDiffPanel;
      }
      if (FocusDiffSide.DATA_KEY.is(dataId)) {
        return myDiffPanel.myCurrentSide == null ? null : myFocusDiffSide;
      }

      return super.getData(dataId);
    }
  }
}
