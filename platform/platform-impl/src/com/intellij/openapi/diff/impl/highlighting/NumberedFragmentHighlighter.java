/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.diff.impl.FragmentNumberGutterIconRenderer;
import com.intellij.openapi.diff.impl.fragments.Fragment;
import com.intellij.openapi.diff.impl.fragments.FragmentHighlighterImpl;
import com.intellij.openapi.diff.impl.fragments.LineFragment;
import com.intellij.openapi.diff.impl.util.TextDiffTypeEnum;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.MultiMap;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;

/**
 * @author irengrig
 *         Date: 8/12/11
 *         Time: 4:01 PM
 */
public class NumberedFragmentHighlighter extends FragmentHighlighterImpl {
  private final boolean myDrawNumber;
  private final Map<Integer, Pair<String, TextDiffTypeEnum>> myLeftPrecalculated;
  private final Map<Integer, Pair<String, TextDiffTypeEnum>> myRightPrecalculated;
  private int myPreviousLineLeft;
  private int myPreviousLineRight;
  private NumberedFragmentHighlighter.MyPropertyChangeListener myPropertyChangeListener;

  public NumberedFragmentHighlighter(DiffMarkup appender1, DiffMarkup appender2, boolean drawNumber) {
    super(appender1, appender2);
    myDrawNumber = drawNumber;
    myLeftPrecalculated = new HashMap<Integer, Pair<String, TextDiffTypeEnum>>();
    myRightPrecalculated = new HashMap<Integer, Pair<String, TextDiffTypeEnum>>();
    myPreviousLineLeft = -1;
    myPreviousLineRight = -1;
  }

  private class MyPropertyChangeListener implements PropertyChangeListener {
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      if (!EditorEx.PROP_FONT_SIZE.equals(evt.getPropertyName())) return;
      if (evt.getOldValue().equals(evt.getNewValue())) return;
      RangeHighlighter[] allHighlighters = myAppender1.getEditor().getMarkupModel().getAllHighlighters();
      resetFont(allHighlighters);
      RangeHighlighter[] allHighlighters2 = myAppender2.getEditor().getMarkupModel().getAllHighlighters();
      resetFont(allHighlighters2);
    }

    private void resetFont(RangeHighlighter[] allHighlighters) {
      for (RangeHighlighter highlighter : allHighlighters) {
        GutterMark renderer = highlighter.getGutterIconRenderer();
        if (renderer instanceof FragmentNumberGutterIconRenderer) {
          ((FragmentNumberGutterIconRenderer)renderer).resetFont(myAppender1.getEditor());
        }
      }
    }
  }

  private TextAttributesKey getColorAttributesKey(final TextDiffTypeEnum textDiffTypeEnum) {
    if (TextDiffTypeEnum.CHANGED.equals(textDiffTypeEnum)) {
      return DiffColors.DIFF_MODIFIED;
    } else if (TextDiffTypeEnum.INSERT.equals(textDiffTypeEnum)) {
      return DiffColors.DIFF_INSERTED;
    } else if (TextDiffTypeEnum.DELETED.equals(textDiffTypeEnum)) {
      return DiffColors.DIFF_DELETED;
    } else if (TextDiffTypeEnum.CONFLICT.equals(textDiffTypeEnum)) {
      return DiffColors.DIFF_CONFLICT;
    } else {
      return null;
    }
  }

  @Override
  protected void highlightFragmentImpl(Fragment fragment) {
    if (! myDrawNumber || fragment.getType() == null || TextDiffTypeEnum.NONE.equals(fragment.getType())) {
      myAppender1.highlightText(fragment, null);
      myAppender2.highlightText(fragment, null);
      return;
    }
    int lineLeft = myAppender1.getDocument().getLineNumber(fragment.getRange(FragmentSide.SIDE1).getStartOffset());
    int lineRight = myAppender2.getDocument().getLineNumber(fragment.getRange(FragmentSide.SIDE2).getStartOffset());
    Pair<String, TextDiffTypeEnum> left = myLeftPrecalculated.get(lineLeft);
    if (myPreviousLineLeft == lineLeft || left == null) {
      myAppender1.highlightText(fragment, null);
    } else {
      // draw border == true for range marker with highlighting and number be set anyway, even if range is empty
      myAppender1.highlightText(fragment, new FragmentNumberGutterIconRenderer(left.getFirst(), getColorAttributesKey(left.getSecond()), myAppender1.getEditor().getScrollPane(), myAppender1.getEditor()));
      myPreviousLineLeft = lineLeft;
    }

    Pair<String, TextDiffTypeEnum> right = myRightPrecalculated.get(lineRight);
    if (myPreviousLineRight == lineRight || right == null) {
      myAppender2.highlightText(fragment, null);
    } else {
      // draw border == true for range marker with highlighting and number be set anyway, even if range is empty
      myAppender2.highlightText(fragment, new FragmentNumberGutterIconRenderer(right.getFirst(), getColorAttributesKey(right.getSecond()), myAppender1.getEditor().getScrollPane(),
                                                                                     myAppender1.getEditor()));
      myPreviousLineRight = lineRight;
    }
  }

  public void addRangeHighlighter(final boolean left, int start, int end, final TextAttributes attributes) {
    if (left) {
      myAppender1.getEditor().getMarkupModel().addRangeHighlighter(start, end, HighlighterLayer.SELECTION - 3, attributes,
                                                                   HighlighterTargetArea.EXACT_RANGE);
    } else {
      myAppender2.getEditor().getMarkupModel().addRangeHighlighter(start, end, HighlighterLayer.SELECTION - 3, attributes,
                                                                         HighlighterTargetArea.EXACT_RANGE);
    }
  }

  public void reset() {
    myLeftPrecalculated.clear();
    myRightPrecalculated.clear();
    myPreviousLineLeft = -1;
    myPreviousLineRight = -1;
  }

  public void precalculateNumbers(List<LineFragment> lines) {
    if (myPropertyChangeListener == null) {
      myPropertyChangeListener = new MyPropertyChangeListener();
      myAppender1.getEditor().addPropertyChangeListener(myPropertyChangeListener);
    }
    final MultiMap<Integer, Pair<Integer, TextDiffTypeEnum>> leftMap = new MultiMap<Integer, Pair<Integer, TextDiffTypeEnum>>();
    final MultiMap<Integer, Pair<Integer, TextDiffTypeEnum>> rightMap = new MultiMap<Integer, Pair<Integer, TextDiffTypeEnum>>();
    
    int cnt = 1;
    for (LineFragment line : lines) {
      if (line.getType() == null || TextDiffTypeEnum.NONE.equals(line.getType())) continue;
      
      final Iterator<Fragment> iterator = line.getChildrenIterator();
      if (iterator == null) {
        TextRange left = line.getRange(FragmentSide.SIDE1);
        TextRange right = line.getRange(FragmentSide.SIDE2);

        leftMap.putValue(myAppender1.getDocument().getLineNumber(left.getStartOffset()), new Pair<Integer, TextDiffTypeEnum>(cnt, line.getType()));
        rightMap.putValue(myAppender2.getDocument().getLineNumber(right.getStartOffset()), new Pair<Integer, TextDiffTypeEnum>(cnt, line.getType()));
        ++ cnt;
        continue;
      }
      while (iterator.hasNext()) {
        final Fragment next = iterator.next();
        if (next.getType() == null || TextDiffTypeEnum.NONE.equals(next.getType())) continue;

        TextRange left = next.getRange(FragmentSide.SIDE1);
        TextRange right = next.getRange(FragmentSide.SIDE2);

        leftMap.putValue(myAppender1.getDocument().getLineNumber(left.getStartOffset()), new Pair<Integer, TextDiffTypeEnum>(cnt, next.getType()));
        rightMap.putValue(myAppender2.getDocument().getLineNumber(right.getStartOffset()), new Pair<Integer, TextDiffTypeEnum>(cnt, next.getType()));
        ++ cnt;
      }
    }
    
    // merge
    merge(leftMap, myLeftPrecalculated);
    merge(rightMap, myRightPrecalculated);
  }

  private void merge(MultiMap<Integer, Pair<Integer, TextDiffTypeEnum>> leftMap,
                     final Map<Integer, Pair<String, TextDiffTypeEnum>> whereTo) {
    for (Map.Entry<Integer, Collection<Pair<Integer, TextDiffTypeEnum>>> entry : leftMap.entrySet()) {
      List<Pair<Integer, TextDiffTypeEnum>> value = (List<Pair<Integer, TextDiffTypeEnum>>) entry.getValue();
      if (value.size() > 1) {
        Pair<Integer, TextDiffTypeEnum> pair1 = value.iterator().next();
        Pair<Integer, TextDiffTypeEnum> pair2 = value.get(value.size() - 1);
        TextDiffTypeEnum type = mergeDiffType(value);
        whereTo.put(entry.getKey(), Pair.create(String.valueOf(pair1.getFirst()) + "-" +
                                                String.valueOf(pair2.getFirst()), type));
      } else {
        Pair<Integer, TextDiffTypeEnum> pair = value.iterator().next();
        whereTo.put(entry.getKey(), Pair.create(String.valueOf(pair.getFirst()), pair.getSecond()));
      }
    }
  }

  private TextDiffTypeEnum mergeDiffType(List<Pair<Integer, TextDiffTypeEnum>> value) {
    TextDiffTypeEnum previous = null;
    for (Pair<Integer, TextDiffTypeEnum> pair : value) {
      if (previous == null) {
        previous = pair.getSecond();
        continue;
      }
      if (! previous.equals(pair.getSecond())) return TextDiffTypeEnum.CHANGED;
    }
    return previous;
  }
  
  public List<Integer> getLeftLines() {
    List<Integer> list = new ArrayList<Integer>(myLeftPrecalculated.keySet());
    Collections.sort(list);
    return list;
  }

  public List<Integer> getRightLines() {
    List<Integer> list = new ArrayList<Integer>(myRightPrecalculated.keySet());
    Collections.sort(list);
    return list;
  }
}
