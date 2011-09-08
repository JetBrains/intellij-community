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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.DiffPanel;
import com.intellij.openapi.diff.ShiftedSimpleContent;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.diff.ex.DiffPanelEx;
import com.intellij.openapi.diff.ex.DiffPanelOptions;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.diff.impl.highlighting.DiffPanelState;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.highlighting.FragmentedDiffPanelState;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.FragmentedEditorHighlighter;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.BeforeAfter;
import com.intellij.util.ui.ButtonlessScrollBarUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * @author irengrig
 *         Date: 6/15/11
 *         Time: 4:00 PM
 */
public class ChangesFragmentedDiffPanel implements Disposable {
  private final JPanel myPanel;
  private final Project myProject;
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

  public ChangesFragmentedDiffPanel(final Project project, String filePath) {
    myProject = project;
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
    dag.add(myPreviousDiff);
    dag.add(myNextDiff);
    myPreviousDiff.registerCustomShortcutSet(myPreviousDiff.getShortcutSet(), myPanel);
    myNextDiff.registerCustomShortcutSet(myNextDiff.getShortcutSet(), myPanel);
    dag.add(new PopupAction());
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, dag, true);
    wrapper.add(toolbar.getComponent(), BorderLayout.EAST);

    myTopPanel.add(wrapper, BorderLayout.CENTER);

    final JPanel wrapperDiffs = new JPanel(new GridBagLayout());
    final JPanel oneMore = new JPanel(new BorderLayout());
    oneMore.add(wrapperDiffs, BorderLayout.NORTH);


    myCurrentHorizontal = myConfiguration.SHORT_DIFF_HORISONTALLY;
    myHorizontal = createPanel(true);
    myVertical = createPanel(false);

    myPanel.add(myTopPanel, BorderLayout.NORTH);
    myPanel.add(getCurrentPanel().getComponent(), BorderLayout.CENTER);
  }

  public void setTitle(String filePath) {
    myTitleLabel.setText(filePath);
  }

  public void refreshData(final PreparedFragmentedContent fragmentedContent) {
    myFragmentedContent = fragmentedContent;

    boolean navigationEnabled = !myFragmentedContent.isOneSide();
    myNextDiff.setEnabled(navigationEnabled);
    myPreviousDiff.setEnabled(navigationEnabled);

    adjustPanelData((DiffPanelImpl)myHorizontal);
    adjustPanelData((DiffPanelImpl) myVertical);

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
    ensurePresentation();
  }

  private String titleText(DiffPanelImpl diffPanel) {
    return myFilePath + " " + diffPanel.getNumDifferencesText();
  }

  private void adjustPanelData(final DiffPanelImpl diffPanel) {
    final FragmentedDiffPanelState diffPanelState = (FragmentedDiffPanelState)diffPanel.getDiffPanelState();
    diffPanelState.setRanges(myFragmentedContent.getLineRanges());
    diffPanel.setContents(new SimpleContent(myFragmentedContent.getSbOld().toString()), new SimpleContent(myFragmentedContent.getSbNew().toString()));
    diffPanel.setLineNumberConvertors(myFragmentedContent.getOldConvertor(), myFragmentedContent.getNewConvertor());
    diffPanel.prefferedSizeByContents(-1);
  }

  private DiffPanel createPanel(final boolean horizontal) {
    final DiffPanel diffPanel = new DiffPanelImpl(null, myProject, false, horizontal){
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

  private class PopupAction extends DumbAwareAction {
    private Component myParent;
    private AnAction myUsual;
    private AnAction myNumbered;

    private PopupAction() {
      super("Settings", "Settings", IconLoader.getIcon("/general/secondaryGroup.png"));
      myUsual = new AnAction("Top | Bottom") {
        @Override
        public void actionPerformed(AnActionEvent e) {
          boolean was = myConfiguration.SHORT_DIFF_HORISONTALLY;
          myConfiguration.SHORT_DIFF_HORISONTALLY = false;
          if (was) {
            ensurePresentation();
          }
        }
      };
      myNumbered = new AnAction("Left | Right") {
        @Override
        public void actionPerformed(AnActionEvent e) {
          boolean was = myConfiguration.SHORT_DIFF_HORISONTALLY;
          myConfiguration.SHORT_DIFF_HORISONTALLY = true;
          if (! was) {
            ensurePresentation();
          }
        }
      };
    }

    public void setParent(Component parent) {
      myParent = parent;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final DefaultActionGroup dag = new DefaultActionGroup();
      dag.add(myUsual);
      dag.add(myNumbered);
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
    if (myCurrentHorizontal != myConfiguration.SHORT_DIFF_HORISONTALLY) {
      final DiffPanel panel = getCurrentPanel();

      myPanel.removeAll();
      myPanel.add(myTopPanel, BorderLayout.NORTH);
      myPanel.add(panel.getComponent(), BorderLayout.CENTER);
      myPanel.revalidate();
      myPanel.repaint();

      myCurrentHorizontal = myConfiguration.SHORT_DIFF_HORISONTALLY;
    }
  }

  private DiffPanel getCurrentPanel() {
    DiffPanel panel;
    if (myConfiguration.SHORT_DIFF_HORISONTALLY) {
      panel = myHorizontal;
    } else {
      panel = myVertical;
    }
    return panel;
  }

  private int getCurrentLogicalLineIdx(final boolean forward) {
    assert ! myLeftLines.isEmpty();

    final Editor editor = ((DiffPanelImpl) getCurrentPanel()).getEditor1();
    Point location = editor.getScrollingModel().getVisibleArea().getLocation();
    LogicalPosition lp = editor.xyToLogicalPosition(location);
    int line = lp.line;
    if (forward) {
      if (line >= myLeftLines.get(myLeftLines.size() - 1)) {
        return myLeftLines.size() - 1;
      }
      for (int i = myLeftLines.size() - 1; i >= 0; i--) {
        Integer integer = myLeftLines.get(i);
        if (integer <= line) return i + 1;
      }
      return 0;
    } else {
      if (line <= myLeftLines.get(0)) return 0;
      for (int i = 0; i < myLeftLines.size(); i++) {
        Integer integer = myLeftLines.get(i);
        if (integer > line) {
          return i;
        }
      }
      return myLeftLines.size() - 1;
    }
  }

  private class MyPreviousDiffAction extends DumbAwareAction {
    private boolean myEnabled;

    private MyPreviousDiffAction() {
      super("Previous Change", "Previous Change", IconLoader.getIcon("/actions/previousOccurence.png"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      int currentLogicalLineIdx = getCurrentLogicalLineIdx(false);
      int nextLineIdx = currentLogicalLineIdx == 0 ? 0 : currentLogicalLineIdx - 1;
      int rightIndex = nextLineIdx >= myRightLines.size() ? (myRightLines.size() - 1) : nextLineIdx;

      DiffPanelImpl panel = (DiffPanelImpl) getCurrentPanel();
      panel.getSideView(FragmentSide.SIDE1).scrollToFirstDiff(myLeftLines.get(nextLineIdx));
      panel.getSideView(FragmentSide.SIDE2).scrollToFirstDiff(myRightLines.get(rightIndex));
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
      super("Next Change", "Next Change", IconLoader.getIcon("/actions/nextOccurence.png"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      int currentLogicalLineIdx = getCurrentLogicalLineIdx(true);
      int nextLineIdx = currentLogicalLineIdx == (myLeftLines.size() - 1) ? currentLogicalLineIdx : currentLogicalLineIdx + 1;
      int rightIndex = nextLineIdx >= myRightLines.size() ? (myRightLines.size() - 1) : nextLineIdx;

      DiffPanelImpl panel = (DiffPanelImpl) getCurrentPanel();
      panel.getSideView(FragmentSide.SIDE1).scrollToFirstDiff(myLeftLines.get(nextLineIdx));
      panel.getSideView(FragmentSide.SIDE2).scrollToFirstDiff(myRightLines.get(rightIndex));
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
