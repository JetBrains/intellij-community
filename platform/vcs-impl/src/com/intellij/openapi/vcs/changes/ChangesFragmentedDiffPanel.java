/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diff.DiffPanel;
import com.intellij.openapi.diff.ShiftedSimpleContent;
import com.intellij.openapi.diff.ex.DiffPanelEx;
import com.intellij.openapi.diff.ex.DiffPanelOptions;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.diff.impl.DiffSideView;
import com.intellij.openapi.diff.impl.IgnoreSpaceEnum;
import com.intellij.openapi.diff.impl.fragments.Fragment;
import com.intellij.openapi.diff.impl.fragments.FragmentList;
import com.intellij.openapi.diff.impl.highlighting.DiffPanelState;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.highlighting.FragmentedDiffPanelState;
import com.intellij.openapi.diff.impl.util.TextDiffTypeEnum;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.FragmentedEditorHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DialogWrapperDialog;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.BeforeAfter;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.ButtonlessScrollBarUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/**
 * @author irengrig
 *         Date: 6/15/11
 *         Time: 4:00 PM
 */
public class ChangesFragmentedDiffPanel implements Disposable {
  private static final int SHORT_DIFF_DIVIDER_POLYGONS_OFFSET = 2;

  private final JPanel myPanel;
  private final Project myProject;
  private final JComponent myParent;
  private PreparedFragmentedContent myFragmentedContent;
  private final String myFilePath;
  private final VcsConfiguration myConfiguration;

  private DiffPanel myHorizontal;
  private DiffPanel myVertical;
  private boolean myCurrentHorizontal;
  private JPanel myTopPanel;
  private List<Integer> myLeftLines;
  private List<Integer> myRightLines;
  private final MyNextDiffAction myNextDiff;
  private final MyPreviousDiffAction myPreviousDiff;
  private final JLabel myTitleLabel;
  private ChangesFragmentedDiffPanel.PresentationState myPresentationState;
  private DumbAwareAction myNavigateAction;

  public ChangesFragmentedDiffPanel(final Project project, String filePath, JComponent parent) {
    myProject = project;
    myParent = parent;
    myConfiguration = VcsConfiguration.getInstance(myProject);
    myFilePath = filePath;

    myPanel = new JPanel(new BorderLayout());
    myNextDiff = new MyNextDiffAction();
    myPreviousDiff = new MyPreviousDiffAction();
    myTitleLabel = new JLabel(myFilePath);
  }

  @Override
  public void dispose() {
    // to remove links to editor that is in scrolling helper
    myPanel.removeAll();
    myHorizontal = null;
    myVertical = null;
    myPreviousDiff.unregisterCustomShortcutSet(myParent);
    myNextDiff.unregisterCustomShortcutSet(myParent);
  }

  public void buildUi() {
    myTopPanel = new JPanel(new BorderLayout());
    final JPanel wrapper = new JPanel();
    //final BoxLayout boxLayout = new BoxLayout(wrapper, BoxLayout.X_AXIS);
    wrapper.setLayout(new BorderLayout());
    myTitleLabel.setBorder(BorderFactory.createEmptyBorder(1, 2, 0, 0));
    wrapper.add(myTitleLabel, BorderLayout.WEST);
    DefaultActionGroup dag = new DefaultActionGroup();
    myPreviousDiff.copyShortcutFrom(ActionManager.getInstance().getAction("PreviousDiff"));
    myNextDiff.copyShortcutFrom(ActionManager.getInstance().getAction("NextDiff"));
    dag.add(new MyChangeContextAction());
    dag.add(myPreviousDiff);
    dag.add(myNextDiff);
    createNavigateAction();
    dag.add(myNavigateAction);
    myPreviousDiff.registerCustomShortcutSet(myPreviousDiff.getShortcutSet(), myPanel);
    myNextDiff.registerCustomShortcutSet(myNextDiff.getShortcutSet(), myPanel);

    dag.add(new PopupAction());
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, dag, true);
    wrapper.add(toolbar.getComponent(), BorderLayout.EAST);

    myTopPanel.add(wrapper, BorderLayout.CENTER);

    final JPanel wrapperDiffs = new JPanel(new GridBagLayout());
    final JPanel oneMore = new JPanel(new BorderLayout());
    oneMore.add(wrapperDiffs, BorderLayout.NORTH);

    myCurrentHorizontal = myConfiguration.SHORT_DIFF_HORIZONTALLY;
    myHorizontal = createPanel(true);
    myVertical = createPanel(false);

    myPanel.add(myTopPanel, BorderLayout.NORTH);
    myPanel.add(getCurrentPanel().getComponent(), BorderLayout.CENTER);

    myPreviousDiff.registerCustomShortcutSet(myPreviousDiff.getShortcutSet(), myParent);
    myNextDiff.registerCustomShortcutSet(myNextDiff.getShortcutSet(), myParent);
  }

  private void createNavigateAction() {
    myNavigateAction = new DumbAwareAction("Edit Source", "Edit Source", AllIcons.Actions.EditSource) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final boolean enabled = getEnabled();
        if (enabled) {
          OpenFileDescriptor descriptor = null;
          if (myFragmentedContent != null && myFragmentedContent.getFile() != null) {
            final DiffPanel panel = myCurrentHorizontal ? myHorizontal : myVertical;

            final DiffSideView side = ((DiffPanelImpl)panel).getCurrentSide();
            if (side == null || side.getEditor() == null) return;

            final boolean isAfter = FragmentSide.SIDE2.equals(side.getSide());
            if (isAfter) {
              final LogicalPosition position = side.getEditor().getCaretModel().getLogicalPosition();
              final int line = position.line;
              final Integer converted = myFragmentedContent.getNewConvertor().convert(line);
              descriptor = new OpenFileDescriptor(myProject, myFragmentedContent.getFile(), converted, position.column);
            } else {
              if (((DiffPanelImpl) panel).getEditor1().getDocument().getTextLength() == 0) {
                FileEditorManager.getInstance(myProject).openTextEditor(new OpenFileDescriptor(myProject, myFragmentedContent.getFile(), 0), true);
                return;
              }

              final CaretModel model = side.getEditor().getCaretModel();
              final FragmentList fragments = ((DiffPanelImpl)panel).getFragments();
              final int line = model.getLogicalPosition().line;
              //final int offset = side.getEditor().getDocument().getLineStartOffset(line);
              final int offset =  model.getOffset();

              BeforeAfter<Integer> current = null;
              final List<BeforeAfter<Integer>> ranges = myFragmentedContent.getLineRanges();
              for (BeforeAfter<Integer> range : ranges) {
                if (range.getBefore() > line) break;
                current = range;
              }
              if (current == null) return;
              final Fragment at = fragments.getFragmentAt(offset, FragmentSide.SIDE1, Condition.TRUE);
              if (at == null) return;
              final TextRange opposite = at.getRange(FragmentSide.SIDE2);

              final int lineInNew = ((DiffPanelImpl)panel).getEditor2().getDocument().getLineNumber(opposite.getStartOffset());

              int correctLine;
              int column;
              if (at.getType() == null || TextDiffTypeEnum.NONE.equals(at.getType())) {
                column = model.getLogicalPosition().column;
                final int startIn1 =
                  ((DiffPanelImpl)panel).getEditor1().getDocument().getLineNumber(at.getRange(FragmentSide.SIDE1).getStartOffset());
                correctLine = lineInNew + line - startIn1;
              }
              else {
                column = 0;
                correctLine = Math.max(lineInNew, current.getAfter());
              }

              final Integer converted = myFragmentedContent.getNewConvertor().convert(correctLine);
              descriptor = new OpenFileDescriptor(myProject, myFragmentedContent.getFile(), converted, column);
            }
          }
          if (descriptor == null) return;
          final OpenFileDescriptor finalDescriptor = descriptor;
          final Runnable runnable = new Runnable() {
            @Override
            public void run() {
              FileEditorManager.getInstance(myProject).openTextEditor(finalDescriptor, true);
            }
          };
          if (! ModalityState.NON_MODAL.equals(ModalityState.current())) {
            final Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
            if (window instanceof DialogWrapperDialog) {
              final DialogWrapper wrapper = ((DialogWrapperDialog)window).getDialogWrapper();
              if (wrapper != null) {
                Disposer.dispose(wrapper.getDisposable());
                wrapper.close(DialogWrapper.CANCEL_EXIT_CODE);
                ApplicationManager.getApplication().invokeLater(runnable, ModalityState.NON_MODAL, myProject.getDisposed());
                return;
              }
            }
          }
          runnable.run();
        }
      }

      @Override
      public void update(AnActionEvent e) {
        super.update(e);
        final boolean enabled = getEnabled();
        e.getPresentation().setEnabled(enabled);
      }

      private boolean getEnabled() {
        final DiffPanel panel = myCurrentHorizontal ? myHorizontal : myVertical;
        if (panel == null) return false;
        final DiffSideView side = ((DiffPanelImpl)panel).getCurrentSide();
        if (side == null || side.getEditor() == null) return false;
        if (side.getEditor() != null) {
          return true;
        }
        return false;
      }
    };
  }

  public void setTitle(String filePath) {
    myTitleLabel.setText(filePath);
  }

  public void refreshData(final PreparedFragmentedContent fragmentedContent) {
    myPresentationState = new PresentationState();
    myFragmentedContent = fragmentedContent;

    boolean navigationEnabled = !myFragmentedContent.isOneSide();
    myNextDiff.setEnabled(navigationEnabled);
    myPreviousDiff.setEnabled(navigationEnabled);

    adjustPanelData((DiffPanelImpl)myHorizontal);
    adjustPanelData((DiffPanelImpl)myVertical);
    if (((DiffPanelImpl) myHorizontal).getEditor1() != null) {
      myNavigateAction.registerCustomShortcutSet(CommonShortcuts.getEditSource(), ((DiffPanelImpl) myHorizontal).getEditor1().getComponent());
      myNavigateAction.registerCustomShortcutSet(CommonShortcuts.getEditSource(), ((DiffPanelImpl) myVertical).getEditor1().getComponent());
    }
    if (((DiffPanelImpl) myHorizontal).getEditor2() != null) {
      myNavigateAction.registerCustomShortcutSet(CommonShortcuts.getEditSource(), ((DiffPanelImpl) myHorizontal).getEditor2().getComponent());
      myNavigateAction.registerCustomShortcutSet(CommonShortcuts.getEditSource(), ((DiffPanelImpl) myVertical).getEditor2().getComponent());
    }

    DiffPanel currentPanel = getCurrentPanel();
    FragmentedDiffPanelState state = (FragmentedDiffPanelState)((DiffPanelImpl)currentPanel).getDiffPanelState();
    myTitleLabel.setText(titleText((DiffPanelImpl)currentPanel));
    myLeftLines = state.getLeftLines();
    myRightLines = state.getRightLines();

    FragmentedEditorHighlighter bh = fragmentedContent.getBeforeHighlighter();
    if (bh != null) {
      ((EditorEx) ((DiffPanelImpl) currentPanel).getEditor1()).setHighlighter(bh);
    }
    FragmentedEditorHighlighter ah = fragmentedContent.getAfterHighlighter();
    if (ah != null) {
      ((EditorEx) ((DiffPanelImpl) currentPanel).getEditor2()).setHighlighter(ah);
    }
    if (((DiffPanelImpl) currentPanel).getEditor1() != null) {
      highlightTodo(true, fragmentedContent.getBeforeTodoRanges());
    }
    if (((DiffPanelImpl) currentPanel).getEditor2() != null) {
      highlightTodo(false, fragmentedContent.getAfterTodoRanges());
    }
    ensurePresentation();
    softWraps(myConfiguration.SOFT_WRAPS_IN_SHORT_DIFF);
  }

  private void softWraps(final boolean value) {
    DiffPanel currentPanel = getCurrentPanel();
    if (((DiffPanelImpl) currentPanel).getEditor1() != null) {
      ((DiffPanelImpl) myHorizontal).getEditor1().getSettings().setUseSoftWraps(value);
      ((DiffPanelImpl) myVertical).getEditor1().getSettings().setUseSoftWraps(value);
    }
    if (((DiffPanelImpl) currentPanel).getEditor2() != null) {
      ((DiffPanelImpl) myHorizontal).getEditor2().getSettings().setUseSoftWraps(value);
      ((DiffPanelImpl) myVertical).getEditor2().getSettings().setUseSoftWraps(value);
    }
  }

  private void highlightTodo(boolean left, List<Pair<TextRange, TextAttributes>> todoRanges) {
    FragmentedDiffPanelState panelState = (FragmentedDiffPanelState)((DiffPanelImpl)myHorizontal).getDiffPanelState();
    FragmentedDiffPanelState panelState2 = (FragmentedDiffPanelState)((DiffPanelImpl)myVertical).getDiffPanelState();
    for (Pair<TextRange, TextAttributes> range : todoRanges) {
      TextAttributes second = range.getSecond().clone();
      panelState.addRangeHighlighter(left, range.getFirst().getStartOffset(), range.getFirst().getEndOffset(), second);
      panelState2.addRangeHighlighter(left, range.getFirst().getStartOffset(), range.getFirst().getEndOffset(), second);
    }
  }

  private String titleText(DiffPanelImpl diffPanel) {
    return myFilePath + " " + diffPanel.getNumDifferencesText();
  }

  private void adjustPanelData(final DiffPanelImpl diffPanel) {
    final FragmentedDiffPanelState diffPanelState = (FragmentedDiffPanelState)diffPanel.getDiffPanelState();
    diffPanelState.setRanges(myFragmentedContent.getLineRanges());
    diffPanel.setContents(myFragmentedContent.createBeforeContent(), myFragmentedContent.createAfterContent());
    diffPanel.setLineNumberConvertors(myFragmentedContent.getOldConvertor(), myFragmentedContent.getNewConvertor());
    diffPanel.prefferedSizeByContents(-1);
  }

  private DiffPanel createPanel(final boolean horizontal) {
    final DiffPanel diffPanel = new DiffPanelImpl(null, myProject, false, horizontal, SHORT_DIFF_DIVIDER_POLYGONS_OFFSET, null) {
      @Override
      protected DiffPanelState createDiffPanelState(@NotNull Disposable parentDisposable) {
        return new FragmentedDiffPanelState(this, myProject, ! horizontal, parentDisposable);
      }
    };
    diffPanel.enableToolbar(false);
    diffPanel.removeStatusBar();
    DiffPanelOptions o = ((DiffPanelEx)diffPanel).getOptions();
    o.setRequestFocusOnNewContent(false);
    Disposer.register(this, diffPanel);
    return diffPanel;
  }

  public void away() {
    myPreviousDiff.unregisterCustomShortcutSet(myParent);
    myNextDiff.unregisterCustomShortcutSet(myParent);
  }

  private class PresentationState {
    private IgnoreSpaceEnum myIgnoreSpace;
    private boolean myHorizontal;
    private int myContextLines;
    private boolean mySoftWraps;

    private PresentationState() {
      myIgnoreSpace = myConfiguration.SHORT_DIFF_IGNORE_SPACE;
      myHorizontal = ChangesFragmentedDiffPanel.this.myCurrentHorizontal;
      myContextLines = myConfiguration.SHORT_DIFF_EXTRA_LINES;
      mySoftWraps = myConfiguration.SOFT_WRAPS_IN_SHORT_DIFF;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      PresentationState that = (PresentationState)o;

      if (myContextLines != that.myContextLines) return false;
      if (myHorizontal != that.myHorizontal) return false;
      if (mySoftWraps != that.mySoftWraps) return false;
      if (myIgnoreSpace != that.myIgnoreSpace) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myIgnoreSpace != null ? myIgnoreSpace.hashCode() : 0;
      result = 31 * result + (myHorizontal ? 1 : 0);
      result = 31 * result + myContextLines;
      result = 31 * result + (mySoftWraps ? 1 : 0);
      return result;
    }
  }

  public void refreshPresentation() {
    // 1. vertical 2. number of lines 3. soft wraps (4. ignore spaces)
    PresentationState current = new PresentationState();
    if (myFragmentedContent != null && ! Comparing.equal(myPresentationState, current)) {
      recalculatePresentation();
    } else {
      ensurePresentation();
    }
    myPreviousDiff.registerCustomShortcutSet(myPreviousDiff.getShortcutSet(), myParent);
    myNextDiff.registerCustomShortcutSet(myNextDiff.getShortcutSet(), myParent);
  }

  private static class MyScrollingHelper {
    private JScrollBar myLeftScroll;
    private int myMaxColumnsLeft;
    private int myMaxColumnsRight;
    private List<ScrollingModel> myLeftModels;
    private List<ScrollingModel> myRightModels;
    private Editor myEditor;
    private boolean myInScrolling;

    private final List<Editor> myLeftEditors;
    private final List<Editor> myRightEditors;

    private boolean myByLeft;
    private int myIdxLeft;
    private int myIdxRight;

    private MyScrollingHelper() {
      myLeftScroll = new JScrollBar(JScrollBar.HORIZONTAL);
      myLeftScroll.setUI(ButtonlessScrollBarUI.createNormal());

      myLeftEditors = new ArrayList<Editor>();
      myRightEditors = new ArrayList<Editor>();

      myMaxColumnsLeft = 0;
      myMaxColumnsRight = 0;
      myLeftModels = new ArrayList<ScrollingModel>();
      myRightModels = new ArrayList<ScrollingModel>();
    }

    public void nextPanel(final BeforeAfter<ShiftedSimpleContent> diff, final DiffPanel diffPanel) {
      final Editor editor1 = ((DiffPanelImpl)diffPanel).getEditor1();
      myLeftModels.add(editor1.getScrollingModel());
      final Editor editor2 = ((DiffPanelImpl)diffPanel).getEditor2();
      myRightModels.add(editor2.getScrollingModel());
      if (myEditor == null) {
        myEditor = editor1;
      }
      myLeftEditors.add(editor1);
      myRightEditors.add(editor2);
      ((EditorEx) editor1).setHorizontalScrollbarVisible(false);
      ((EditorEx) editor1).setVerticalScrollbarVisible(false);
      ((EditorEx) editor1).getScrollPane().setWheelScrollingEnabled(false);
      ((EditorEx) editor2).setHorizontalScrollbarVisible(false);
      ((EditorEx) editor2).setVerticalScrollbarVisible(false);
      ((EditorEx) editor2).getScrollPane().setWheelScrollingEnabled(false);
    }

    private void recalculateMaxValues() {
      myIdxLeft = widestEditor(myLeftEditors);
      final Editor leftEditor = myLeftEditors.get(myIdxLeft);
      final int wholeWidth = leftEditor.getContentComponent().getWidth();
      final Rectangle va = leftEditor.getScrollingModel().getVisibleArea();
      final int visibleLeft = leftEditor.xyToVisualPosition(new Point(va.width, 0)).column;

      myMaxColumnsLeft = (int)(visibleLeft * ((double) wholeWidth / va.getWidth()));

      myIdxRight = widestEditor(myRightEditors);
      final Editor rightEditor = myRightEditors.get(myIdxRight);
      final int wholeWidthRight = rightEditor.getContentComponent().getWidth();
      final Rectangle vaRight = rightEditor.getScrollingModel().getVisibleArea();
      final int visibleRight = rightEditor.xyToVisualPosition(new Point(va.width, 0)).column;

      myMaxColumnsRight = (int)(visibleRight * ((double) wholeWidthRight / vaRight.getWidth()));

      myByLeft = ! (myMaxColumnsLeft <= visibleLeft);
      if (! myByLeft) {
        // check right editor
        if (myLeftScroll.getVisibleAmount() != visibleRight) {
          myLeftScroll.setVisibleAmount(visibleRight);
        }
        myLeftScroll.setMaximum(myMaxColumnsRight);
      } else {
        if (myLeftScroll.getVisibleAmount() != visibleLeft) {
          myLeftScroll.setVisibleAmount(visibleLeft);
        }
        myLeftScroll.setMaximum(myMaxColumnsLeft);
      }
    }

    private int widestEditor(final List<Editor> editors) {
      int maxWidth = 0;
      int idxMax = 0;
      for (int i = 0; i < editors.size(); i++) {
        Editor editor = editors.get(i);
        final int wholeWidth = editor.getContentComponent().getWidth();
        if (wholeWidth > maxWidth) {
          maxWidth = wholeWidth;
          idxMax = i;
        }
      }
      return idxMax;
    }

    public void afterPanelsAdded() {
      myLeftScroll.setMinimum(0);
      myLeftScroll.setMaximum(myMaxColumnsLeft);
      myLeftScroll.addAdjustmentListener(new AdjustmentListener() {
        @Override
        public void adjustmentValueChanged(AdjustmentEvent e) {
          myInScrolling = true;

          final int scrollPosCorrected = myLeftScroll.getValue() + 1;
          if (myByLeft) {
            scrollMain(myLeftScroll.getValue(), myLeftModels);
            scrollOther(scrollPosCorrected, myMaxColumnsLeft, myMaxColumnsRight, myRightModels);
          } else {
            scrollMain(myLeftScroll.getValue(), myRightModels);
            scrollOther(scrollPosCorrected, myMaxColumnsRight, myMaxColumnsLeft, myLeftModels);
          }
          myInScrolling = false;
        }
      });
    }

    private void scrollOther(int scrollPosCorrected, final int maxColumnsOur, int maxColumnsOther, final List<ScrollingModel> models) {
      int pos2;
      if (myLeftScroll.getValue() == 0) {
        pos2 = 0;
      } else if ((scrollPosCorrected + myLeftScroll.getModel().getExtent()) >= maxColumnsOur) {
        pos2 = maxColumnsOther + 1;
      } else {
        pos2 = (int) (scrollPosCorrected * (((double) maxColumnsOther)/ maxColumnsOur));
      }
      final int pointX2 = (int)myEditor.logicalPositionToXY(new LogicalPosition(0, pos2)).getX();
      for (ScrollingModel model : models) {
        model.scrollHorizontally(pointX2);
      }
    }

    private void scrollMain(int scrollPosCorrected, final List<ScrollingModel> models) {
      final int pointX = (int)myEditor.logicalPositionToXY(new LogicalPosition(0, scrollPosCorrected)).getX();
      for (ScrollingModel model : models) {
        model.scrollHorizontally(pointX);
      }
    }

    public void onEditorAreaChanged(VisibleAreaEvent e) {
      if (myInScrolling) return;
      recalculateMaxValues();
    }

    public JScrollBar getLeftScroll() {
      return myLeftScroll;
    }
  }

  private static class SplittersSynchronizer {
    private final List<Splitter> mySplitters;
    private final List<Splitter> myListening;

    private SplittersSynchronizer() {
      mySplitters = new ArrayList<Splitter>();
      myListening = new ArrayList<Splitter>();
    }

    public void addListening(final Splitter splitter) {
      myListening.add(splitter);
    }

    public void add(final Splitter splitter, final Editor editor) {
      editor.getScrollingModel().addVisibleAreaListener(new MyVisibleAreaListener(mySplitters.size()));
      mySplitters.add(splitter);
    }

    private void adjustTo(final int idx) {
      final Splitter target = mySplitters.get(idx);
      for (int i = 0; i < mySplitters.size(); i++) {
        final Splitter splitter = mySplitters.get(i);
        splitter.setProportion(target.getProportion());
      }
      for (Splitter splitter : myListening) {
        splitter.setProportion(target.getProportion());
      }
    }

    private class MyVisibleAreaListener implements VisibleAreaListener {
      private final int myIdx;

      private MyVisibleAreaListener(int idx) {
        myIdx = idx;
      }

      @Override
      public void visibleAreaChanged(VisibleAreaEvent e) {
        adjustTo(myIdx);
      }
    }
  }

  private final static Icon ourIcon = PlatformIcons.CHECK_ICON;
  
  private class PopupAction extends DumbAwareAction {
    private Component myParent;
    private AnAction myUsual;
    private AnAction myNumbered;
    private final ChangesFragmentedDiffPanel.MyUseSoftWrapsAction mySoftWrapsAction;

    private PopupAction() {
      super("Settings", "Settings", AllIcons.General.SecondaryGroup);
      myUsual = new AnAction("Top | Bottom", "", AllIcons.General.Mdot_empty) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          boolean was = myConfiguration.SHORT_DIFF_HORIZONTALLY;
          myConfiguration.SHORT_DIFF_HORIZONTALLY = false;
          ensurePresentation();
        }

        @Override
        public void update(AnActionEvent e) {
          super.update(e);
          e.getPresentation().setIcon(myConfiguration.SHORT_DIFF_HORIZONTALLY ? AllIcons.General.Mdot_empty : AllIcons.General.Mdot);
        }
      };
      myNumbered = new AnAction("Left | Right", "", AllIcons.General.Mdot_empty) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          boolean was = myConfiguration.SHORT_DIFF_HORIZONTALLY;
          myConfiguration.SHORT_DIFF_HORIZONTALLY = true;
          ensurePresentation();
        }

        @Override
        public void update(AnActionEvent e) {
          super.update(e);
          e.getPresentation().setIcon(myConfiguration.SHORT_DIFF_HORIZONTALLY ? AllIcons.General.Mdot : AllIcons.General.Mdot_empty);
        }
      };
      mySoftWrapsAction = new MyUseSoftWrapsAction(myConfiguration.SOFT_WRAPS_IN_SHORT_DIFF);
    }

    public void setParent(Component parent) {
      myParent = parent;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final DefaultActionGroup dag = new DefaultActionGroup();
      dag.add(myUsual);
      dag.add(myNumbered);
      dag.add(new Separator());
      dag.add(mySoftWrapsAction);
      final ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(null, dag, e.getDataContext(),
                                                                                       JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                                                                                       false);
      if (e.getInputEvent() instanceof MouseEvent) {
        popup.show(new RelativePoint((MouseEvent)e.getInputEvent()));
      } else {
        // todo correct
        /*final Dimension dimension = popup.getContent().getPreferredSize();
        final Point at = new Point(-dimension.width / 2, 0);
        popup.show(new RelativePoint(myParent, at));*/
        popup.showInBestPositionFor(e.getDataContext());
      }
    }
  }

  /*private class MyShowSettingsButton extends ActionButton implements DumbAware {
    MyShowSettingsButton() {
      this(new PopupAction(), new Presentation(), ActionPlaces.CHANGES_LOCAL_DIFF_SETTINGS, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
    }

    MyShowSettingsButton(AnAction action, Presentation presentation, String place, @NotNull Dimension minimumSize) {
      super(action, presentation, place, minimumSize);
      ((PopupAction) getAction()).setParent(this);
      myPresentation.setIcon(IconLoader.getIcon("/general/secondaryGroup.png"));
    }
  }*/

  private void ensurePresentation() {
    if (myCurrentHorizontal != myConfiguration.SHORT_DIFF_HORIZONTALLY) {
      final DiffPanel panel = getCurrentPanel();

      myPanel.removeAll();
      myPanel.add(myTopPanel, BorderLayout.NORTH);
      myPanel.add(panel.getComponent(), BorderLayout.CENTER);
      myPanel.revalidate();
      myPanel.repaint();

      myCurrentHorizontal = myConfiguration.SHORT_DIFF_HORIZONTALLY;
    }
  }

  private DiffPanel getCurrentPanel() {
    DiffPanel panel;
    if (myConfiguration.SHORT_DIFF_HORIZONTALLY) {
      panel = myHorizontal;
    } else {
      panel = myVertical;
    }
    return panel;
  }

  private int getCurrentLogicalLineIdx(final boolean forward) {
    assert ! myLeftLines.isEmpty();

    BeforeAfter<Integer> editorLines = getEditorLines();
    if (forward) {
      Integer line = editorLines.getAfter();
      if (line >= myLeftLines.get(myLeftLines.size() - 1)) {
        return myLeftLines.size() - 1;
      }
      for (int i = myLeftLines.size() - 1; i >= 0; i--) {
        Integer integer = myLeftLines.get(i);
        if (integer <= line) return i;
      }
      return 0;
    } else {
      Integer line = editorLines.getBefore();
      if (line <= myLeftLines.get(0)) return 0;
      for (int i = 0; i < myLeftLines.size(); i++) {
        Integer integer = myLeftLines.get(i);
        if (integer >= line) {
          return i;
        }
      }
      return myLeftLines.size() - 1;
    }
  }

  private BeforeAfter<Integer> getEditorLines() {
    final Editor editor = ((DiffPanelImpl) getCurrentPanel()).getEditor1();
    Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    final int offset = editor.getScrollingModel().getVerticalScrollOffset();

    int leftPixels = offset % editor.getLineHeight();

    final Point start = visibleArea.getLocation();
    final LogicalPosition startLp = editor.xyToLogicalPosition(start);
    final Point location = new Point(start.x + visibleArea.width, start.y + visibleArea.height);
    final LogicalPosition lp = editor.xyToLogicalPosition(location);

    int curStartLine = startLp.line == (editor.getDocument().getLineCount() - 1) ? startLp.line : (startLp.line + 1);
    int cutEndLine = lp.line == 0 ? 0 : lp.line - 1;

    boolean commonPartOk = leftPixels == 0 || startLp.line == lp.line;
    return new BeforeAfter<Integer>(commonPartOk && startLp.softWrapLinesOnCurrentLogicalLine == 0 ? startLp.line : curStartLine,
                                    commonPartOk && lp.softWrapLinesOnCurrentLogicalLine == 0 ? lp.line : cutEndLine);
    /*if (leftPixels == 0 || startLp.line == lp.line) {
      return new BeforeAfter<Integer>(startLp.line, lp.line);
    } else {
      return new BeforeAfter<Integer>(curStartLine, cutEndLine);
    }*/
  }

  private final static int[] ourMarks = {1,2,4,8,-1};
  public static final Hashtable<Integer,JLabel> LABELS = new Hashtable<Integer, JLabel>();
  public static final int ALL_VALUE = 5;

  static {
    LABELS.put(1, markLabel("1"));
    LABELS.put(2, markLabel("2"));
    LABELS.put(3, markLabel("4"));
    LABELS.put(4, markLabel("8"));
    LABELS.put(ALL_VALUE, markLabel("All"));
  }

  private static JLabel markLabel(final String text) {
    JLabel label = new JLabel(text);
    label.setFont(UIUtil.getLabelFont());
    return label;
  }

  private class MyUseSoftWrapsAction extends ToggleAction implements DumbAware {
    private final Icon myIcon;

    private MyUseSoftWrapsAction(boolean turned) {
      super("Use soft wraps", "", ourIcon);
      myIcon = ourIcon;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myConfiguration.SOFT_WRAPS_IN_SHORT_DIFF;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myConfiguration.SOFT_WRAPS_IN_SHORT_DIFF = state;
      softWraps(myConfiguration.SOFT_WRAPS_IN_SHORT_DIFF);
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setIcon(myConfiguration.SOFT_WRAPS_IN_SHORT_DIFF ? myIcon : null);
    }
  }

  private class MyChangeContextAction extends DumbAwareAction {
    private MyChangeContextAction() {
      super("More/Less Lines...", "More/Less Lines...", AllIcons.Actions.Expandall);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      JPanel result = new JPanel(new BorderLayout());
      JLabel label = new JLabel("Lines around:");
      label.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 0));
      JPanel wrapper = new JPanel(new BorderLayout());
      wrapper.add(label, BorderLayout.NORTH);
      result.add(wrapper, BorderLayout.WEST);
      final JSlider slider = new JSlider(JSlider.HORIZONTAL, 1, 5, 1);
      slider.setMinorTickSpacing(1);
      slider.setPaintTicks(true);
      slider.setPaintTrack(true);
      slider.setSnapToTicks(true);
      UIUtil.setSliderIsFilled(slider, true);
      slider.setPaintLabels(true);
      slider.setLabelTable(LABELS);
      result.add(slider, BorderLayout.CENTER);
      final VcsConfiguration configuration = VcsConfiguration.getInstance(myProject);
      for (int i = 0; i < ourMarks.length; i++) {
        int mark = ourMarks[i];
        if (mark == configuration.SHORT_DIFF_EXTRA_LINES) {
          slider.setValue(i + 1);
        }
      }
      JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(result, slider).createPopup();
      popup.setFinalRunnable(new Runnable() {
        @Override
        public void run() {
          int value = slider.getModel().getValue();
          if (configuration.SHORT_DIFF_EXTRA_LINES != ourMarks[value - 1]) {
            configuration.SHORT_DIFF_EXTRA_LINES = ourMarks[value - 1];
            try {
              recalculatePresentation();
            } catch (ChangeOutdatedException e) {
              //
            }
          }
        }
      });
      InputEvent inputEvent = e.getInputEvent();
      if (inputEvent instanceof MouseEvent) {
        int width = result.getPreferredSize().width;
        MouseEvent inputEvent1 = (MouseEvent)inputEvent;
        Point point1 = new Point(inputEvent1.getX() - width / 2, inputEvent1.getY());
        RelativePoint point = new RelativePoint(inputEvent1.getComponent(), point1);
        popup.show(point);
      } else {
        popup.showInBestPositionFor(e.getDataContext());
      }
    }
  }

  private void recalculatePresentation() {
    myFragmentedContent.recalculate();
    refreshData(myFragmentedContent);
  }

  private class MyPreviousDiffAction extends DumbAwareAction {
    private boolean myEnabled;

    private MyPreviousDiffAction() {
      super("Previous Change", "Previous Change", AllIcons.Actions.PreviousOccurence);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      int currentLogicalLineIdx = getCurrentLogicalLineIdx(false);
      int nextLineIdx = currentLogicalLineIdx == 0 ? 0 : currentLogicalLineIdx - 1;
      int rightIndex = nextLineIdx >= myRightLines.size() ? (myRightLines.size() - 1) : nextLineIdx;

      DiffPanelImpl panel = (DiffPanelImpl) getCurrentPanel();
      panel.getSideView(FragmentSide.SIDE1).scrollToFirstDiff(myLeftLines.get(nextLineIdx));
      //panel.getSideView(FragmentSide.SIDE2).scrollToFirstDiff(myRightLines.get(rightIndex));
    }

    public void setEnabled(boolean enabled) {
      myEnabled = enabled;
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(myEnabled);
    }
  }

  private class MyNextDiffAction extends DumbAwareAction {
    private boolean myEnabled;
    
    private MyNextDiffAction() {
      super("Next Change", "Next Change", AllIcons.Actions.NextOccurence);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      int currentLogicalLineIdx = getCurrentLogicalLineIdx(true);
      int nextLineIdx = currentLogicalLineIdx == (myLeftLines.size() - 1) ? currentLogicalLineIdx : currentLogicalLineIdx + 1;
      int rightIndex = nextLineIdx >= myRightLines.size() ? (myRightLines.size() - 1) : nextLineIdx;

      DiffPanelImpl panel = (DiffPanelImpl) getCurrentPanel();
      panel.getSideView(FragmentSide.SIDE1).scrollToFirstDiff(myLeftLines.get(nextLineIdx));
      //panel.getSideView(FragmentSide.SIDE2).scrollToFirstDiff(myRightLines.get(rightIndex));
    }

    public void setEnabled(boolean enabled) {
      myEnabled = enabled;
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(myEnabled);
    }
  }

  public JPanel getPanel() {
    return myPanel;
  }
}
