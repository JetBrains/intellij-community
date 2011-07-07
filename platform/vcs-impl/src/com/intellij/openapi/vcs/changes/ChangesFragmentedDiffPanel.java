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
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.diff.impl.ContentChangeListener;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.diff.impl.highlighting.FragmentedDiffPanelState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.BeforeAfter;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.ButtonlessScrollBarUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
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
  private final FragmentedContent myFragmentedContent;
  private final String myFilePath;
  private final DiffPanelHolder myMyDiffPanelHolder;

  public ChangesFragmentedDiffPanel(final Project project, final FragmentedContent fragmentedContent,
                                    final LinkedList<DiffPanel> cache, String filePath) {
    myProject = project;
    myMyDiffPanelHolder = new DiffPanelHolder(cache, myProject) {
      @Override
      protected DiffPanel create() {
        final DiffPanel diffPanel = super.create();
        ((DiffPanelImpl) diffPanel).setDiffPanelState(new FragmentedDiffPanelState((ContentChangeListener)diffPanel, project));
        return diffPanel;
      }
    };
    myFragmentedContent = fragmentedContent;
    myFilePath = filePath;
    assert ! myFragmentedContent.getRanges().isEmpty();

    myPanel = new JPanel(new BorderLayout());
  }

  @Override
  public void dispose() {
    myMyDiffPanelHolder.resetPanels();
    // to remove links to editor that is in scrolling helper
    myPanel.removeAll();
  }

  public void buildUi() {
    final JPanel topPanel = new JPanel(new BorderLayout());
    final JPanel wrapper = new JPanel();
    final BoxLayout boxLayout = new BoxLayout(wrapper, BoxLayout.X_AXIS);
    wrapper.setLayout(boxLayout);
    final JLabel label = new JLabel(myFilePath);
    label.setBorder(BorderFactory.createEmptyBorder(1,2,0,0));
    wrapper.add(label);
    topPanel.add(wrapper, BorderLayout.CENTER);

    myPanel.add(topPanel, BorderLayout.NORTH);

    final JPanel wrapperDiffs = new JPanel(new GridBagLayout());
    final JPanel oneMore = new JPanel(new BorderLayout());
    oneMore.add(wrapperDiffs, BorderLayout.NORTH);

    final LineNumberConvertor oldConvertor = new LineNumberConvertor();
    final LineNumberConvertor newConvertor = new LineNumberConvertor();
    final StringBuilder sbOld = new StringBuilder();
    final StringBuilder sbNew = new StringBuilder();
    // line starts
    final List<BeforeAfter<Integer>> ranges = new ArrayList<BeforeAfter<Integer>>();

    BeforeAfter<Integer> lines = new BeforeAfter<Integer>(0,0);
    for (BeforeAfter<TextRange> lineNumbers : myFragmentedContent.getRanges()) {
      ranges.add(lines);
      oldConvertor.put(lines.getBefore(), lineNumbers.getBefore().getStartOffset());
      newConvertor.put(lines.getAfter(), lineNumbers.getAfter().getStartOffset());

      final Document document = myFragmentedContent.getBefore();
      if (sbOld.length() > 0) {
        sbOld.append('\n');
      }
      sbOld.append(document.getText(new TextRange(document.getLineStartOffset(lineNumbers.getBefore().getStartOffset()),
                                                  document.getLineEndOffset(lineNumbers.getBefore().getEndOffset()))));

      final Document document1 = myFragmentedContent.getAfter();
      if (sbNew.length() > 0) {
        sbNew.append('\n');
      }
      sbNew.append(document1.getText(new TextRange(document1.getLineStartOffset(lineNumbers.getAfter().getStartOffset()),
                                    document1.getLineEndOffset(lineNumbers.getAfter().getEndOffset()))));
      int before = lines.getBefore() + lineNumbers.getBefore().getEndOffset() - lineNumbers.getBefore().getStartOffset() + 1;
      int after = lines.getAfter() + lineNumbers.getAfter().getEndOffset() - lineNumbers.getAfter().getStartOffset() + 1;
      lines = new BeforeAfter<Integer>(before, after);
    }
    ranges.add(new BeforeAfter<Integer>(lines.getBefore() == 0 ? 0 : lines.getBefore() - 1,
                                        lines.getAfter() == 0 ? 0 : lines.getAfter() - 1));

    final DiffPanel diffPanel = myMyDiffPanelHolder.getOrCreate();
    final FragmentedDiffPanelState diffPanelState = (FragmentedDiffPanelState)((DiffPanelImpl) diffPanel).getDiffPanelState();
    diffPanelState.setRanges(ranges);
    diffPanel.setContents(new SimpleContent(sbOld.toString()), new SimpleContent(sbNew.toString()));
    ((DiffPanelImpl) diffPanel).setLineNumberConvertors(oldConvertor, newConvertor);
    ((DiffPanelImpl) diffPanel).prefferedSizeByContents(-1);

    myPanel.add(diffPanel.getComponent(), BorderLayout.CENTER);
  }

  private static class LineNumberConvertor implements Convertor<Integer, Integer> {
    private final TreeMap<Integer, Integer> myFragmentStarts;

    private LineNumberConvertor() {
      myFragmentStarts = new TreeMap<Integer, Integer>();
    }

    public void put(final int start, final int offset) {
      myFragmentStarts.put(start, offset);
    }

    @Override
    public Integer convert(Integer o) {
      final Map.Entry<Integer, Integer> floor = myFragmentStarts.floorEntry(o);
      return floor == null ? o : floor.getValue() + o - floor.getKey();
    }
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
