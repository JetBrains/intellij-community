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

import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.impl.ContentChangeListener;
import com.intellij.openapi.diff.impl.fragments.FragmentHighlighterImpl;
import com.intellij.openapi.diff.impl.fragments.FragmentListImpl;
import com.intellij.openapi.diff.impl.fragments.LineFragment;
import com.intellij.openapi.diff.impl.processing.DiffPolicy;
import com.intellij.openapi.diff.impl.processing.TextCompareProcessor;
import com.intellij.openapi.diff.impl.splitter.LineBlocks;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.BeforeAfter;
import com.intellij.util.diff.FilesTooBigForDiffException;

import java.util.ArrayList;
import java.util.Iterator;
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

  public FragmentedDiffPanelState(ContentChangeListener changeListener, Project project) {
    super(changeListener, project);
  }

  @Override
  public void setContents(DiffContent content1, DiffContent content2) {
    myAppender1.setContent(content1);
    myAppender2.setContent(content2);
  }

  private LineBlocks addMarkup(final List<LineFragment> lines) {
    for (Iterator<LineFragment> iterator = lines.iterator(); iterator.hasNext();) {
      LineFragment line = iterator.next();
      final FragmentHighlighterImpl fragmentHighlighter = new FragmentHighlighterImpl(myAppender1, myAppender2, !iterator.hasNext());
      line.highlight(fragmentHighlighter);
    }
    ArrayList<LineFragment> allLineFragments = new ArrayList<LineFragment>();
    for (Iterator<LineFragment> iterator = lines.iterator(); iterator.hasNext();) {
      LineFragment lineFragment = iterator.next();
      allLineFragments.add(lineFragment);
      lineFragment.addAllDescendantsTo(allLineFragments);
    }
    myFragmentList = FragmentListImpl.fromList(allLineFragments);
    return LineBlocks.fromLineFragments(allLineFragments);
  }

  private void resetMarkup() {
    myAppender1.resetHighlighters();
    myAppender2.resetHighlighters();
  }

  public LineBlocks updateEditors() throws FilesTooBigForDiffException {
    resetMarkup();
    if (myAppender1.getEditor() == null || myAppender2.getEditor() == null) {
      return LineBlocks.EMPTY;
    }

    int previousBefore = -1;
    int previousAfter = -1;

    final List<BeforeAfter<TextRange>> ranges = new ArrayList<BeforeAfter<TextRange>>();
    for (int i = 0; i < myRanges.size(); i++) {
      final BeforeAfter<Integer> start = lineStarts(i);
      final BeforeAfter<Integer> end = (i == myRanges.size() - 1) ?
        new BeforeAfter<Integer>(myAppender1.getDocument().getTextLength(), myAppender2.getDocument().getTextLength()) :
        lineStarts(i + 1);

      ranges.add(new BeforeAfter<TextRange>(new TextRange(start.getBefore(), end.getBefore()),
                                            new TextRange(start.getAfter(), end.getAfter())));

      if (previousBefore > 0 && previousAfter > 0) {
        myAppender1.setSeparatorMarker(previousBefore);
        myAppender2.setSeparatorMarker(previousAfter);
      }
      previousBefore = myRanges.get(i).getBefore();
      previousAfter = myRanges.get(i).getAfter();
    }

    final PresetBlocksDiffPolicy diffPolicy = new PresetBlocksDiffPolicy(DiffPolicy.LINES_WO_FORMATTING);
    // shouldn't be set since component is reused. or no getDiffPolicy for delegate initialization
    //setDiffPolicy(diffPolicy);
    diffPolicy.setRanges(ranges);

    return addMarkup(new TextCompareProcessor(myComparisonPolicy, diffPolicy).process(myAppender1.getText(), myAppender2.getText()));
  }

  private BeforeAfter<Integer> lineStarts(int i) {
    return new BeforeAfter<Integer>(myAppender1.getDocument().getLineStartOffset(myRanges.get(i).getBefore()),
      myAppender2.getDocument().getLineStartOffset(myRanges.get(i).getAfter()));
  }

  public void setRanges(List<BeforeAfter<Integer>> ranges) {
    myRanges = new ArrayList<BeforeAfter<Integer>>();
    if (! ranges.isEmpty()) {
      if (ranges.get(0).getAfter() != 0 && ranges.get(0).getBefore() != 0) {
        myRanges.add(new BeforeAfter<Integer>(0,0));
      }
    }
    myRanges.addAll(ranges);
  }
}
