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
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.diff.DiffPanel;
import com.intellij.openapi.diff.ShiftedSimpleContent;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.BeforeAfter;
import com.intellij.util.ui.ButtonlessScrollBarUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * @author irengrig
 *         Date: 6/15/11
 *         Time: 4:00 PM
 */
public class ChangesFragmentedDiffPanel implements Disposable {
  private final JPanel myPanel;
  private final Project myProject;
  private final List<BeforeAfter<ShiftedSimpleContent>> myDiffs;
  private final String myFilePath;
  private final DiffPanelHolder myMyDiffPanelHolder;

  public ChangesFragmentedDiffPanel(final Project project, final List<BeforeAfter<ShiftedSimpleContent>> diffs,
                                    final LinkedList<DiffPanel> cache, String filePath) {
    myProject = project;
    myMyDiffPanelHolder = new DiffPanelHolder(cache, myProject);
    myDiffs = diffs;
    myFilePath = filePath;
    assert ! myDiffs.isEmpty();

    myPanel = new JPanel(new BorderLayout());

    buildUi();
  }

  @Override
  public void dispose() {
    myMyDiffPanelHolder.resetPanels();
    // to remove links to editor that is in scrolling helper
    myPanel.removeAll();
  }

  private void buildUi() {
    final JPanel topPanel = new JPanel(new BorderLayout());
    final JPanel wrapper = new JPanel();
    final BoxLayout boxLayout = new BoxLayout(wrapper, BoxLayout.X_AXIS);
    wrapper.setLayout(boxLayout);
    final JLabel label = new JLabel(myFilePath);
    label.setBorder(BorderFactory.createEmptyBorder(1,2,0,0));
    wrapper.add(label);
    topPanel.add(wrapper, BorderLayout.CENTER);

    final MyScrollingHelper scrollingHelper = new MyScrollingHelper();
    topPanel.add(scrollingHelper.getLeftScroll(), BorderLayout.SOUTH);

    myPanel.add(topPanel, BorderLayout.NORTH);

    final JPanel wrapperDiffs = new JPanel(new GridBagLayout());
    final JPanel oneMore = new JPanel(new BorderLayout());
    oneMore.add(wrapperDiffs, BorderLayout.NORTH);
    final JBScrollPane scrollBig =
      new JBScrollPane(oneMore, JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    myPanel.add(scrollBig, BorderLayout.CENTER);
    final GridBagConstraints gb = new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                             new Insets(0, 0, 0, 0), 0, 0);

    final SplittersSynchronizer splittersSynchronizer = new SplittersSynchronizer();

    final VisibleAreaListener val = new VisibleAreaListener() {
      @Override
      public void visibleAreaChanged(VisibleAreaEvent e) {
        oneMore.revalidate();
        oneMore.repaint();
        scrollingHelper.onEditorAreaChanged(e);
      }
    };

    for (int i = 0; i < myDiffs.size(); i++) {
      BeforeAfter<ShiftedSimpleContent> diff = myDiffs.get(i);
      final DiffPanel diffPanel = myMyDiffPanelHolder.getOrCreate();
      ((DiffPanelImpl) diffPanel).noSynchScroll();
      diffPanel.setContents(diff.getBefore(), diff.getAfter());
      ((DiffPanelImpl) diffPanel).prefferedSizeByContents(-1);
      ((DiffPanelImpl) diffPanel).setLineShift(diff.getBefore().getLineShift(), diff.getAfter().getLineShift());
      if (i == 0) {
        ((DiffPanelImpl) diffPanel).getEditor1().getScrollingModel().addVisibleAreaListener(val);
      }

      wrapperDiffs.add(diffPanel.getComponent(), gb);
      ++ gb.gridy;

      scrollingHelper.nextPanel(diff, diffPanel);
      splittersSynchronizer.add(((DiffPanelImpl)diffPanel).getSplitter(), ((DiffPanelImpl) diffPanel).getEditor1());
    }

    scrollingHelper.afterPanelsAdded();
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

  public JPanel getPanel() {
    return myPanel;
  }

  private class MyShowSettingsButton extends ActionButton {
    MyShowSettingsButton() {
      this(new MyShowSettingsAction(), new Presentation(), ActionPlaces.CHANGES_LOCAL_DIFF_SETTINGS, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
    }

    MyShowSettingsButton(AnAction action, Presentation presentation, String place, @NotNull Dimension minimumSize) {
      super(action, presentation, place, minimumSize);
      myPresentation.setIcon(IconLoader.getIcon("/general/secondaryGroup.png"));
    }

    public void hideSettings() {
      /*if (!mySettingsPanel.isVisible()) {
        return;
      }
      AnActionEvent event = new AnActionEvent(
        null, EMPTY_DATA_CONTEXT, ActionPlaces.JAVADOC_INPLACE_SETTINGS, myPresentation, ActionManager.getInstance(), 0
      );
      myAction.actionPerformed(event);*/
    }
  }

  private class MyShowSettingsAction extends ToggleAction {
    private boolean myIsSelected;

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myIsSelected;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myIsSelected = ! myIsSelected;
    }
  }
}
