/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.ide.actions.EditSourceAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.diff.actions.MergeActionGroup;
import com.intellij.openapi.diff.ex.DiffPanelEx;
import com.intellij.openapi.diff.ex.DiffPanelOptions;
import com.intellij.openapi.diff.impl.external.DiffManagerImpl;
import com.intellij.openapi.diff.impl.fragments.Fragment;
import com.intellij.openapi.diff.impl.fragments.FragmentList;
import com.intellij.openapi.diff.impl.highlighting.DiffPanelState;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.processing.HorizontalDiffSplitter;
import com.intellij.openapi.diff.impl.splitter.DiffDividerPaint;
import com.intellij.openapi.diff.impl.splitter.LineBlocks;
import com.intellij.openapi.diff.impl.util.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.UIBasedFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.pom.Navigatable;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.util.containers.CacheOneStepIterator;
import com.intellij.util.containers.Convertor;
import com.intellij.util.diff.FilesTooBigForDiffException;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseListener;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

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

  private static final DiffRequest.ToolbarAddons TOOL_BAR = new DiffRequest.ToolbarAddons() {
    public void customize(DiffToolbar toolbar) {
      ActionManager actionManager = ActionManager.getInstance();
      toolbar.addAction(actionManager.getAction("DiffPanel.Toolbar"));
    }
  };
  private boolean myDisposed = false;
  private final GenericDataProvider myDataProvider;
  private final Project myProject;
  private final boolean myIsHorizontal;
  private CanNotCalculateDiffPanel myNotCalculateDiffPanel;
  private final VisibleAreaListener myVisibleAreaListener;

  public DiffPanelImpl(final Window owner, Project project, boolean enableToolbar, boolean horizontal, int diffDividerPolygonsOffset) {
    myProject = project;
    myIsHorizontal = horizontal;
    myOptions = new DiffPanelOptions(this);
    myPanel = new DiffPanelOuterComponent(TextDiffType.DIFF_TYPES, TOOL_BAR);
    myPanel.disableToolbar(!enableToolbar);
    if (enableToolbar) myPanel.resetToolbar();
    myOwnerWindow = owner;
    myIsSyncScroll = true;
    final boolean v = !horizontal;
    myLeftSide =  new DiffSideView("", this, new CustomLineBorder(UIUtil.getBorderColor(), 1, 0, v ? 0 : 1, v ? 0 : 1));
    myRightSide = new DiffSideView("", this, new CustomLineBorder(UIUtil.getBorderColor(), v ? 0 : 1, v ? 0 : 1, 1, 0));
    myLeftSide.becomeMaster();
    myDiffUpdater = new Rediffers(this);

    myData = createDiffPanelState(this);

    if (horizontal) {
      mySplitter = new DiffSplitter(myLeftSide.getComponent(), myRightSide.getComponent(),
                                    new DiffDividerPaint(this, FragmentSide.SIDE1, diffDividerPolygonsOffset), myData);
    }
    else {
      mySplitter = new HorizontalDiffSplitter(myLeftSide.getComponent(), myRightSide.getComponent());
    }

    myPanel.insertDiffComponent(mySplitter.getComponent(), new MyScrollingPanel());
    myDataProvider = new MyGenericDataProvider(this);
    myPanel.setDataProvider(myDataProvider);

    final ComparisonPolicy comparisonPolicy = getComparisonPolicy();
    final ComparisonPolicy defaultComparisonPolicy = DiffManagerImpl.getInstanceEx().getComparisonPolicy();

    if (defaultComparisonPolicy != null && comparisonPolicy != defaultComparisonPolicy) {
      setComparisonPolicy(defaultComparisonPolicy);
    }
    myVisibleAreaListener = new VisibleAreaListener() {
      @Override
      public void visibleAreaChanged(VisibleAreaEvent e) {
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

    // EditSourceAction is not enabled in modal context, so we need to register it here explicitly
    new EditSourceAction().registerCustomShortcutSet(CommonShortcuts.getEditSource(), myPanel, this);
  }

  protected DiffPanelState createDiffPanelState(@NotNull Disposable parentDisposable) {
    return new DiffPanelState(this, myProject, parentDisposable);
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
    myPanel.setPreferredHeightGetter(new Getter<Integer>() {
      @Override
      public Integer get() {
        final int size1 = getEditor1() == null ? 10 : getEditor1().getComponent().getPreferredSize().height;
        final int size2 = getEditor2() == null ? 10 : getEditor2().getComponent().getPreferredSize().height;
        final int lineHeight = getEditor1() == null ? getEditor2().getLineHeight() : getEditor1().getLineHeight();
        final int size = Math.max(size1, size2);
        if (maximumLines > 0) {
          return Math.min(size, maximumLines * lineHeight);
        }
        return size;
      }
    });
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

  public void removeStatusBar() {
    myPanel.removeStatusBar();
  }

  public void enableToolbar(final boolean value) {
    myPanel.disableToolbar(!value);
  }

  public void setLineNumberConvertors(final Convertor<Integer, Integer> old, final Convertor<Integer, Integer> newConvertor) {
    if (getEditor1() != null) {
      ((EditorGutterComponentEx) getEditor1().getGutter()).setLineNumberConvertor(old);
    }
    if (getEditor2() != null) {
      ((EditorGutterComponentEx) getEditor2().getGutter()).setLineNumberConvertor(newConvertor);
    }
  }
  // todo pay attention here
  private static DiffHighlighterFactory createHighlighter(FileType contentType, VirtualFile file, Project project) {
    return new DiffHighlighterFactoryImpl(contentType, file, project);
  }

  void rediff() {
    try {
      if (myNotCalculateDiffPanel != null) {
        myPanel.removeTopComponent(myNotCalculateDiffPanel);
      }
      setLineBlocks(myData.updateEditors());
    }
    catch (FilesTooBigForDiffException e) {
      setTooBigFileErrorContents();
    }
  }

  public void setTooBigFileErrorContents() {
    setLineBlocks(LineBlocks.EMPTY);
    myNotCalculateDiffPanel = new CanNotCalculateDiffPanel();
    myPanel.insertTopComponent(myNotCalculateDiffPanel);
  }

  public void setPatchAppliedApproximately() {
    if (myNotCalculateDiffPanel == null) {
      myPanel.insertTopComponent(new DiffIsApproximate());
    }
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
    return getLineBlocks().getBeginnings(side);
  }

  public void dispose() {
    myDisposed = true;
    myDiffUpdater.dispose();
    Disposer.dispose(myScrollSupport);
    Disposer.dispose(myData);
    myPanel.cancelScrollEditors();
    JComponent component = myPanel.getBottomComponent();
    if (component instanceof Disposable) {
      Disposer.dispose((Disposable)component);
    }
    myPanel.setBottomComponent(null);
    myPanel.setDataProvider(null);
    myPanel.setScrollingPanel(null);
  }

  public JComponent getComponent() {
    return myPanel;
  }

  private void updateStatusBar() {
    myPanel.setStatusBarText(getNumDifferencesText());
  }

  public String getNumDifferencesText() {
    return DiffBundle.message("diff.count.differences.status.text", getLineBlocks().getCount());
  }

  public boolean hasDifferences() {
    return getLineBlocks().getCount() > 0 || myNotCalculateDiffPanel != null;
  }

  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return myCurrentSide.getFocusableComponent();
  }

  public int getContentsNumber() {
    return 2;
  }

  @Override
  public boolean acceptsType(DiffViewerType type) {
    return DiffViewerType.contents.equals(type);
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
      public void dispose() {
        myFontSizeSynchronizer.stopSynchronize(editor);
      }
    });
    source.addDisposable(new Disposable() {
      public void dispose() {
        if (visibleAreaListener != null) {
          scrollingModel.removeVisibleAreaListener(visibleAreaListener);
          scrollingModel.removeVisibleAreaListener(myVisibleAreaListener);
        }
        editor.getContentComponent().removeMouseListener(mouseListener);
      }
    });
  }

  public void setCurrentSide(@NotNull DiffSideView viewSide) {
    LOG.assertTrue(viewSide != myCurrentSide);
    if (myCurrentSide != null) myCurrentSide.beSlave();
    myCurrentSide = viewSide;
  }

  public DiffSideView getCurrentSide() { return myCurrentSide; }

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

  public DiffSideView getSideView(FragmentSide side) {
    if (side == FragmentSide.SIDE1) {
      return myLeftSide;
    }
    if (side == FragmentSide.SIDE2) return myRightSide;
    throw new IllegalArgumentException(String.valueOf(side));
  }

  public LineBlocks getLineBlocks() { return myLineBlocks; }

  public void setDiffRequest(DiffRequest data) {
    myDiffRequest = data;
    if (data.getHints().contains(DiffTool.HINT_DO_NOT_IGNORE_WHITESPACES)) {
      setComparisonPolicy(ComparisonPolicy.DEFAULT, false);
    }
    myDataProvider.putData(myDiffRequest.getGenericData());

    IdeFocusManager fm = IdeFocusManager.getInstance(myProject);
    boolean isEditor1Focused = getEditor1() != null
                               && fm.getFocusedDescendantFor(getEditor1().getComponent()) != null;

    boolean isEditor2Focused = myData.getContent2() != null
                               && getEditor2() != null
                               && fm.getFocusedDescendantFor(getEditor2().getComponent()) != null;

    setContents(data.getContents()[0], data.getContents()[1]);
    setTitle1(data.getContentTitles()[0]);
    setTitle2(data.getContentTitles()[1]);
    setWindowTitle(myOwnerWindow, data.getWindowTitle());
    //if (isBinaryOrUIEditors(data)) {
    //  myPanel.removeStatusBar();
    //  //myPanel.disableToolbar(true);
    //} else {
    //  myPanel.addStatusBar();
    //}
    data.customizeToolbar(myPanel.resetToolbar());
    myPanel.registerToolbarActions();

    final JComponent oldBottomComponent = myPanel.getBottomComponent();
    if (oldBottomComponent instanceof Disposable) {
      Disposer.dispose((Disposable)oldBottomComponent);
    }
    final JComponent newBottomComponent = data.getBottomComponent();
    myPanel.setBottomComponent(newBottomComponent);


    if (myIsRequestFocus) {
      if (isEditor1Focused || isEditor2Focused) {
        Editor e = isEditor2Focused ? getEditor2() : getEditor1();
        if (e != null) {
          fm.requestFocus(e.getContentComponent(), true);
        }
      }

      myPanel.requestScrollEditors();
    }
  }

  private boolean isBinaryOrUIEditors(DiffRequest data) {
    final DiffContent[] contents = data.getContents();
    return contents[0].isBinary()
           || contents[1].isBinary()
           || contents[0].getContentType() instanceof UIBasedFileType
           || contents[1].getContentType() instanceof UIBasedFileType;
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

  public void setRequestFocus(boolean isRequestFocus) {
    myIsRequestFocus = isRequestFocus;
  }

  private class MyScrollingPanel implements DiffPanelOuterComponent.ScrollingPanel {

    public void scrollEditors() {
      getOptions().onNewContent(myCurrentSide);
      final DiffNavigationContext scrollContext = myDiffRequest == null ? null :
        (DiffNavigationContext) myDiffRequest.getGenericData().get(DiffTool.SCROLL_TO_LINE.getName());
      if (scrollContext == null) {
        scrollCurrentToFirstDiff();
      } else {
        final Document document = myRightSide.getEditor().getDocument();

        final FragmentList fragmentList = getFragments();

        final Application application = ApplicationManager.getApplication();
        application.executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            final ChangedLinesIterator changedLinesIterator = new ChangedLinesIterator(fragmentList.iterator(), document, false);
            final CacheOneStepIterator<Pair<Integer, String>> cacheOneStepIterator =
              new CacheOneStepIterator<Pair<Integer, String>>(changedLinesIterator);
            final NavigationContextChecker checker = new NavigationContextChecker(cacheOneStepIterator, scrollContext);
            int line = checker.contextMatchCheck();
            if (line < 0) {
              // this will work for the case, when spaces changes are ignored, and corresponding fragments are not reported as changed
              // just try to find target line  -> +-
              final ChangedLinesIterator changedLinesIterator2 = new ChangedLinesIterator(fragmentList.iterator(), document, true);
              final CacheOneStepIterator<Pair<Integer, String>> cacheOneStepIterator2 =
                new CacheOneStepIterator<Pair<Integer, String>>(changedLinesIterator2);
              final NavigationContextChecker checker2 = new NavigationContextChecker(cacheOneStepIterator2, scrollContext);
              line = checker2.contextMatchCheck();
            }
            final int finalLine = line;
            final ModalityState modalityState = myOwnerWindow == null ? ModalityState.NON_MODAL : ModalityState.stateForComponent(myOwnerWindow);
            application.invokeLater(new Runnable() {
              @Override
              public void run() {
                if (finalLine >= 0) {
                  final int line = myLineBlocks.transform(myRightSide.getSide(), finalLine);
                  myLeftSide.scrollToFirstDiff(line);
                } else {
                  scrollCurrentToFirstDiff();
                }
              }
            }, modalityState);
          }
        });
      }
    }

    private void scrollCurrentToFirstDiff() {
      int[] fragments = getFragmentBeginnings();
      if (fragments.length > 0) myCurrentSide.scrollToFirstDiff(fragments[0]);
    }
  }

  private static class ChangedLinesIterator implements Iterator<Pair<Integer, String>> {
    private final Document myDocument;
    private final boolean myIgnoreFragmentsType;
    private final Iterator<Fragment> myFragmentsIterator;
    private final List<Pair<Integer, String>> myBuffer;

    private ChangedLinesIterator(Iterator<Fragment> fragmentsIterator, Document document, final boolean ignoreFragmentsType) {
      myFragmentsIterator = fragmentsIterator;
      myDocument = document;
      myIgnoreFragmentsType = ignoreFragmentsType;
      myBuffer = new LinkedList<Pair<Integer, String>>();
    }

    @Override
    public boolean hasNext() {
      return !myBuffer.isEmpty() || myFragmentsIterator.hasNext();
    }

    @Override
    public Pair<Integer, String> next() {
      if (! myBuffer.isEmpty()) {
        return myBuffer.remove(0);
      }

      Fragment fragment = null;
      while (myFragmentsIterator.hasNext()) {
        fragment = myFragmentsIterator.next();
        final TextDiffTypeEnum type = fragment.getType();
        if (! myIgnoreFragmentsType && (type == null || TextDiffTypeEnum.DELETED.equals(type) || TextDiffTypeEnum.NONE.equals(type))) continue;
        break;
      }
      if (fragment == null) return null;

      final TextRange textRange = fragment.getRange(FragmentSide.SIDE2);
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          final int startLine = myDocument.getLineNumber(textRange.getStartOffset());
          final int endFragmentOffset = textRange.getEndOffset();
          final int endLine = myDocument.getLineNumber(endFragmentOffset);
          for (int i = startLine; i <= endLine; i++) {
            int lineEndOffset = myDocument.getLineEndOffset(i);
            final int lineStartOffset = myDocument.getLineStartOffset(i);
            if (lineEndOffset > endFragmentOffset && endFragmentOffset == lineStartOffset) {
              lineEndOffset = endFragmentOffset;
            }
            if (lineStartOffset > lineEndOffset) continue;
            String text = myDocument.getText().substring(lineStartOffset, lineEndOffset);
            myBuffer.add(new Pair<Integer, String>(i, text));
          }
        }
      });
      if (myBuffer.isEmpty()) return null;
      return myBuffer.remove(0);
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  private static class NavigationContextChecker {
    private final Iterator<Pair<Integer, String>> myChangedLinesIterator;
    private final DiffNavigationContext myContext;

    private NavigationContextChecker(Iterator<Pair<Integer, String>> changedLinesIterator, DiffNavigationContext context) {
      myChangedLinesIterator = changedLinesIterator;
      myContext = context;
    }

    public int contextMatchCheck() {
      final Iterable<String> contextLines = myContext.getPreviousLinesIterable();

      // we ignore spaces.. at least at start/end, since some version controls could ignore their changes when doing annotate
      final Iterator<String> iterator = contextLines.iterator();
      if (iterator.hasNext()) {
        String contextLine = iterator.next().trim();

        while (myChangedLinesIterator.hasNext()) {
          final Pair<Integer, String> pair = myChangedLinesIterator.next();
          if (pair.getSecond().trim().equals(contextLine)) {
            if (! iterator.hasNext()) break;
            contextLine = iterator.next().trim();
          }
        }
        if (iterator.hasNext()) {
          return -1;
        }
      }
      if (! myChangedLinesIterator.hasNext()) return -1;

      final String targetLine = myContext.getTargetString().trim();
      while (myChangedLinesIterator.hasNext()) {
        final Pair<Integer, String> pair = myChangedLinesIterator.next();
        if (pair.getSecond().trim().equals(targetLine)) {
          return pair.getFirst();
        }
      }
      return -1;
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
      if (PlatformDataKeys.NAVIGATABLE.is(dataId)) {
        final DiffSideView currentSide = myDiffPanel.myCurrentSide;
        if (currentSide != null) {
          return new DiffNavigatable(currentSide);
        }
      }

      return super.getData(dataId);
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

    public DiffNavigatable(DiffSideView side) {
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
      final OpenFileDescriptor descriptor = mySide.getCurrentOpenFileDescriptor();
      if (descriptor != null) {
        showSource(descriptor);
      }
    }
  }
}
