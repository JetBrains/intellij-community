/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.fragments;

import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.diff.actions.MergeOperations;
import com.intellij.openapi.diff.impl.highlighting.DiffMarkup;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.TextRange;

import java.util.Iterator;

public class FragmentHighlighterImpl implements FragmentHighlighter {
  private final DiffMarkup myAppender1;
  private final DiffMarkup myAppender2;
  private final boolean myIsLast;

  public FragmentHighlighterImpl(DiffMarkup appender1, DiffMarkup appender2, boolean isLast) {
    myAppender1 = appender1;
    myAppender2 = appender2;
    myIsLast = isLast;
  }

  public void highlightInline(final InlineFragment fragment) {
    myAppender1.highlightText(fragment, true);
    myAppender2.highlightText(fragment, true);
  }

  public void highlightLine(final LineFragment fragment) {
    addModifyActions(fragment, myAppender1, myAppender2);
    final Iterator<Fragment> iterator = fragment.getChildrenIterator();
    if (iterator == null) {
      myAppender1.highlightText(fragment, false);
      myAppender2.highlightText(fragment, false);
    }
    else {
      for (; iterator.hasNext();) {
        Fragment childFragment = iterator.next();
        childFragment.highlight(this);
      }
    }
    if (fragment.isEqual() && myIsLast) return;
    addBottomLine(fragment, myAppender1, fragment.getEndLine1());
    addBottomLine(fragment, myAppender2, fragment.getEndLine2());
  }

  private void addModifyActions(final LineFragment fragment, DiffMarkup wrapper, DiffMarkup otherWrapper) {
    if (fragment.isEqual()) return;
    if (fragment.isHasLineChildren()) return;
    TextRange range = fragment.getRange(wrapper.getSide());
    TextRange otherRange = fragment.getRange(wrapper.getSide().otherSide());
    Document document = wrapper.getDocument();
    Document otherDocument = otherWrapper.getDocument();
    wrapper.addAction(MergeOperations.mostSensible(document, otherDocument, range, otherRange), range.getStartOffset());
    otherWrapper.addAction(MergeOperations.mostSensible(otherDocument, document, otherRange, range), otherRange.getStartOffset());
  }

  private void addBottomLine(final Fragment fragment, DiffMarkup appender, int endLine) {
    if (endLine <= 0) return;
    TextRange range = fragment.getRange(appender.getSide());
    appender.addLineMarker(endLine - 1, getRangeType(fragment, range));
  }

  private TextAttributesKey getRangeType(final Fragment fragment, final TextRange range) {
    if (range.getLength() == 0) return DiffColors.DIFF_DELETED;
    return fragment.getType() == null ? null : DiffColors.DIFF_MODIFIED;
  }
}
