// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.fragments;

import com.intellij.openapi.diff.actions.MergeOperations;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.diff.impl.highlighting.DiffMarkup;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.util.TextDiffTypeEnum;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

public class FragmentHighlighterImpl implements FragmentHighlighter {
  protected final DiffMarkup myAppender1;
  protected final DiffMarkup myAppender2;
  private boolean myIsLast;

  public FragmentHighlighterImpl(DiffMarkup appender1, DiffMarkup appender2) {
    myAppender1 = appender1;
    myAppender2 = appender2;
    myIsLast = false;
  }

  public void setIsLast(boolean isLast) {
    myIsLast = isLast;
  }

  @Override
  public void highlightInline(final InlineFragment fragment) {
    highlightFragmentImpl(fragment);
  }

  protected void highlightFragmentImpl(final Fragment fragment) {
    myAppender1.highlightText(fragment, null);
    myAppender2.highlightText(fragment, null);
  }

  @Override
  public void highlightLine(final LineFragment fragment) {
    highlightFragmentImpl(fragment);

    addModifyActions(fragment, myAppender1, myAppender2);
    final Iterator<Fragment> iterator = fragment.getChildrenIterator();
    if (iterator != null) {
      while (iterator.hasNext()) {
        Fragment childFragment = iterator.next();
        childFragment.highlight(this);
      }
    }
    if (fragment.isEqual() && myIsLast) return;
    addSeparatingLine(fragment, myAppender1, fragment.getStartingLine1(), fragment.getEndLine1());
    addSeparatingLine(fragment, myAppender2, fragment.getStartingLine2(), fragment.getEndLine2());
  }

  private static void addModifyActions(final LineFragment fragment, DiffMarkup wrapper, DiffMarkup otherWrapper) {
    if (fragment.isEqual()) return;
    if (fragment.isHasLineChildren()) return;
    FragmentSide side = wrapper.getSide();
    TextRange range = fragment.getRange(side);
    TextRange otherRange = fragment.getRange(side.otherSide());
    Document document = wrapper.getDocument();
    Document otherDocument = otherWrapper.getDocument();
    wrapper.addAction(MergeOperations.mostSensible(document, otherDocument, range, otherRange, side), range.getStartOffset());
    otherWrapper.addAction(MergeOperations.mostSensible(otherDocument, document, otherRange, range, side.otherSide()), otherRange.getStartOffset());
  }

  private static void addSeparatingLine(@NotNull LineFragment fragment, @NotNull DiffMarkup appender, int startLine, int endLine) {
    if (endLine <= 0) return;
    TextDiffTypeEnum type = fragment.getType();
    appender.addLineMarker(endLine - 1, type == null ? null : DiffUtil.makeTextDiffType(fragment), SeparatorPlacement.BOTTOM);
    if (fragment.getRange(appender.getSide()).getLength() > 0 && startLine > 0) {
      appender.addLineMarker(startLine, type == null ? null : DiffUtil.makeTextDiffType(fragment), SeparatorPlacement.TOP);
    }
  }
}
