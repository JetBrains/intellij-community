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
package com.intellij.openapi.diff.impl.highlighting;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.impl.ContentChangeListener;
import com.intellij.openapi.diff.impl.fragments.FragmentListImpl;
import com.intellij.openapi.diff.impl.fragments.LineFragment;
import com.intellij.openapi.diff.impl.processing.DiffPolicy;
import com.intellij.openapi.diff.impl.processing.TextCompareProcessor;
import com.intellij.openapi.diff.impl.splitter.LineBlocks;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.BeforeAfter;
import com.intellij.util.Consumer;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * for read-only documents..
 *
 * @author irengrig
 *         Date: 7/6/11
 *         Time: 7:31 PM
 */
public class FragmentedDiffPanelState extends DiffPanelState {
  // fragment _start_ lines
  private List<BeforeAfter<Integer>> myRanges;
  private final NumberedFragmentHighlighter myFragmentHighlighter;
  private FragmentSeparatorsPositionConsumer mySeparatorsPositionConsumer;

  public FragmentedDiffPanelState(ContentChangeListener changeListener,
                                  Project project,
                                  int diffDividerPolygonsOffset,
                                  boolean drawNumber,
                                  @NotNull Disposable parentDisposable) {
    super(changeListener, project, diffDividerPolygonsOffset, parentDisposable);
    myFragmentHighlighter = new NumberedFragmentHighlighter(myAppender1, myAppender2, drawNumber);
    mySeparatorsPositionConsumer = new FragmentSeparatorsPositionConsumer();
  }

  @Override
  public void setContents(DiffContent content1, DiffContent content2) {
    myAppender1.setContent(content1);
    myAppender2.setContent(content2);
    myFragmentHighlighter.reset();
  }

  private LineBlocks addMarkup(final List<LineFragment> lines) {
    myFragmentHighlighter.precalculateNumbers(lines);

    for (Iterator<LineFragment> iterator = lines.iterator(); iterator.hasNext(); ) {
      LineFragment line = iterator.next();
      myFragmentHighlighter.setIsLast(!iterator.hasNext());
      line.highlight(myFragmentHighlighter);
    }
    ArrayList<LineFragment> allLineFragments = new ArrayList<LineFragment>();
    for (LineFragment lineFragment : lines) {
      allLineFragments.add(lineFragment);
      lineFragment.addAllDescendantsTo(allLineFragments);
    }
    myFragmentList = FragmentListImpl.fromList(allLineFragments);
    return LineBlocks.fromLineFragments(allLineFragments);
  }

  public void addRangeHighlighter(final boolean left, int start, int end, final TextAttributes attributes) {
    myFragmentHighlighter.addRangeHighlighter(left, start, end, attributes);
  }

  private void resetMarkup() {
    myAppender1.resetHighlighters();
    myAppender2.resetHighlighters();
  }

  @Nullable
  public LineBlocks updateEditors() throws FilesTooBigForDiffException {
    resetMarkup();
    mySeparatorsPositionConsumer.clear();
    if (myAppender1.getEditor() == null || myAppender2.getEditor() == null) {
      return null;
    }

    int previousBefore = -1;
    int previousAfter = -1;

    final List<BeforeAfter<TextRange>> ranges = new ArrayList<BeforeAfter<TextRange>>();
    for (int i = 0; i < myRanges.size(); i++) {
      final BeforeAfter<Integer> start = lineStarts(i);
      final BeforeAfter<Integer> end = i == myRanges.size() - 1 ?
                                       new BeforeAfter<Integer>(myAppender1.getDocument().getTextLength(),
                                                                myAppender2.getDocument().getTextLength()) :
                                       lineStarts(i + 1);

      ranges.add(new BeforeAfter<TextRange>(new TextRange(start.getBefore(), end.getBefore()),
                                            new TextRange(start.getAfter(), end.getAfter())));

      if (previousBefore > 0 && previousAfter > 0) {
        final int finalPreviousBefore = previousBefore;
        mySeparatorsPositionConsumer.prepare(previousBefore, previousAfter);

        myAppender1.setSeparatorMarker(previousBefore, new Consumer<Integer>() {
          @Override
          public void consume(Integer integer) {
            mySeparatorsPositionConsumer.addLeft(finalPreviousBefore, integer);
          }
        });
        final int finalPreviousAfter = previousAfter;
        myAppender2.setSeparatorMarker(previousAfter, new Consumer<Integer>() {
          @Override
          public void consume(Integer integer) {
            mySeparatorsPositionConsumer.addRight(finalPreviousAfter, integer);
          }
        });
      }
      previousBefore = myRanges.get(i).getBefore();
      previousAfter = myRanges.get(i).getAfter();
    }

    final PresetBlocksDiffPolicy diffPolicy = new PresetBlocksDiffPolicy(DiffPolicy.LINES_WO_FORMATTING);
    // shouldn't be set since component is reused. or no getDiffPolicy for delegate initialization
    //setDiffPolicy(diffPolicy);
    diffPolicy.setRanges(ranges);

    return addMarkup(
      new TextCompareProcessor(myComparisonPolicy, diffPolicy, myHighlightMode).process(myAppender1.getText(), myAppender2.getText()));
  }

  private BeforeAfter<Integer> lineStarts(int i) {
    return new BeforeAfter<Integer>(myAppender1.getDocument().getLineStartOffset(myRanges.get(i).getBefore()),
                                    myAppender2.getDocument().getLineStartOffset(myRanges.get(i).getAfter()));
  }

  public void setRanges(List<BeforeAfter<Integer>> ranges) {
    myRanges = new ArrayList<BeforeAfter<Integer>>();
    if (!ranges.isEmpty()) {
      if (ranges.get(0).getAfter() != 0 && ranges.get(0).getBefore() != 0) {
        myRanges.add(new BeforeAfter<Integer>(0, 0));
      }
    }
    myRanges.addAll(ranges);
  }

  public List<Integer> getLeftLines() {
    return myFragmentHighlighter.getLeftLines();
  }

  public List<Integer> getRightLines() {
    return myFragmentHighlighter.getRightLines();
  }

  public FragmentSeparatorsPositionConsumer getSeparatorsPositionConsumer() {
    return mySeparatorsPositionConsumer;
  }

  @Override
  public void drawOnDivider(Graphics gr, JComponent component) {
    if (myAppender1.getEditor() == null || myAppender2.getEditor() == null) return;

    final int startLeft = getStartVisibleLine(myAppender1.getEditor());
    final int startRight = getStartVisibleLine(myAppender2.getEditor());

    final int width = component.getWidth();
    final int lineHeight = myAppender1.getEditor().getLineHeight();

    final TreeMap<Integer, FragmentSeparatorsPositionConsumer.TornSeparator> left = mySeparatorsPositionConsumer.getLeft();

    final int leftScrollOffset = myAppender1.getEditor().getScrollingModel().getVerticalScrollOffset();
    final int rightScrollOffset = myAppender2.getEditor().getScrollingModel().getVerticalScrollOffset();

    final Graphics g = gr.create();

    try {
      ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      for (Map.Entry<Integer, FragmentSeparatorsPositionConsumer.TornSeparator> entry : left.entrySet()) {
        final FragmentSeparatorsPositionConsumer.TornSeparator tornSeparator = entry.getValue();
        if (tornSeparator.getLeftLine() >= startLeft || tornSeparator.getRightLine() >= startRight) {
          final int leftOffset = tornSeparator.getLeftOffset();
          int leftBaseY =
            myAppender1.getEditor().logicalPositionToXY(new LogicalPosition(tornSeparator.getLeftLine(), 0)).y - lineHeight / 2 -
            leftScrollOffset + myDiffDividerPolygonsOffset;

          final int rightOffset = tornSeparator.getRightOffset();
          int rightBaseY =
            myAppender2.getEditor().logicalPositionToXY(new LogicalPosition(tornSeparator.getRightLine(), 0)).y - lineHeight / 2 -
            rightScrollOffset + myDiffDividerPolygonsOffset;

          int x1 = 0;
          int x2 = width;
          int y1 = leftBaseY + leftOffset;
          int y2 = rightBaseY + rightOffset;

          if (Math.abs(x2 - x1) < Math.abs(y2 - y1)) {
            int dx = TornLineParams.ourDark;
            int dy = TornLineParams.ourLight;
            if (y2 < y1) {
              g.setColor(FragmentBoundRenderer.darkerBorder());
              g.drawLine(x1 + dx, y1 - dy + TornLineParams.ourDark, x2, y2 + TornLineParams.ourDark);
              g.drawLine(x1, y1 - TornLineParams.ourDark, x2 - dx, y2 + dy - TornLineParams.ourDark);

              g.drawLine(x1, y1 + TornLineParams.ourDark, x1 + dx, y1 - dy + TornLineParams.ourDark);
              g.drawLine(x2, y2 - TornLineParams.ourDark, x2 - dx, y2 + dy - TornLineParams.ourDark);

              g.setColor(FragmentBoundRenderer.darkerBorder().darker());
              g.drawLine(x1 + dx, y1 - dy + TornLineParams.ourLight, x2, y2 + TornLineParams.ourLight);
              g.drawLine(x1, y1 - TornLineParams.ourLight, x2 - dx, y2 + dy - TornLineParams.ourLight);

              g.drawLine(x1, y1 + TornLineParams.ourLight, x1 + dx, y1 - dy + TornLineParams.ourLight);
              g.drawLine(x2, y2 - TornLineParams.ourLight, x2 - dx, y2 + dy - TornLineParams.ourLight);
            } else {
              g.setColor(FragmentBoundRenderer.darkerBorder());
              g.drawLine(x1, y1 + TornLineParams.ourDark, x2 - dx, y2 - dy + TornLineParams.ourDark);
              g.drawLine(x1 + dx, y1 + dy - TornLineParams.ourDark, x2, y2 - TornLineParams.ourDark);

              g.drawLine(x2, y2 + TornLineParams.ourDark, x2 - dx, y2 - dy + TornLineParams.ourDark);
              g.drawLine(x1, y1 - TornLineParams.ourDark, x1 + dx, y1 + dy - TornLineParams.ourDark);

              g.setColor(FragmentBoundRenderer.darkerBorder().darker());
              g.drawLine(x1, y1 + TornLineParams.ourLight, x2 - dx, y2 - dy + TornLineParams.ourLight);
              g.drawLine(x1 + dx, y1 + dy - TornLineParams.ourLight, x2, y2 - TornLineParams.ourLight);

              g.drawLine(x2, y2 + TornLineParams.ourLight, x2 - dx, y2 - dy + TornLineParams.ourLight);
              g.drawLine(x1, y1 - TornLineParams.ourLight, x1 + dx, y1 + dy - TornLineParams.ourLight);
            }

          } else {
            g.setColor(FragmentBoundRenderer.darkerBorder());
            g.drawLine(x1, y1 + TornLineParams.ourDark, x2, y2 + TornLineParams.ourDark);
            g.drawLine(x1, y1 - TornLineParams.ourDark, x2, y2 - TornLineParams.ourDark);

            g.setColor(FragmentBoundRenderer.darkerBorder().darker());
            g.drawLine(x1, y1 + TornLineParams.ourLight, x2, y2 + TornLineParams.ourLight);
            g.drawLine(x1, y1 - TornLineParams.ourLight, x2, y2 - TornLineParams.ourLight);
          }
        }
      }
    }
    finally {
      g.dispose();
    }
  }

  private int getStartVisibleLine(final Editor editor) {
    int offset = editor.getScrollingModel().getVerticalScrollOffset();
    LogicalPosition logicalPosition = editor.xyToLogicalPosition(new Point(0, offset));
    return logicalPosition.line;
  }
}
