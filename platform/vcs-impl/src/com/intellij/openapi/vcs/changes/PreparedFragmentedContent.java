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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.highlighter.FragmentedEditorHighlighter;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.BeforeAfter;

import java.util.ArrayList;
import java.util.List;

/**
* Created by IntelliJ IDEA.
* User: Irina.Chernushina
* Date: 9/8/11
* Time: 1:19 PM
*/
public class PreparedFragmentedContent {
  private final LineNumberConvertor oldConvertor;
  private final LineNumberConvertor newConvertor;
  private final StringBuilder sbOld;
  private final StringBuilder sbNew;
  private final List<TextRange> myBeforeFragments;
  private final List<TextRange> myAfterFragments;
  private final List<BeforeAfter<Integer>> myLineRanges;
  private boolean myOneSide;

  private FragmentedEditorHighlighter myBeforeHighlighter;
  private FragmentedEditorHighlighter myAfterHighlighter;

  public PreparedFragmentedContent(final FragmentedContent fragmentedContent) {
    oldConvertor = new LineNumberConvertor();
    newConvertor = new LineNumberConvertor();
    sbOld = new StringBuilder();
    sbNew = new StringBuilder();
    myBeforeFragments = new ArrayList<TextRange>(fragmentedContent.getSize());
    myAfterFragments = new ArrayList<TextRange>(fragmentedContent.getSize());
    myLineRanges = new ArrayList<BeforeAfter<Integer>>();
    fromFragmentedContent(fragmentedContent);
  }

  private void fromFragmentedContent(final FragmentedContent fragmentedContent) {
    myOneSide = fragmentedContent.isOneSide();
    // line starts
    BeforeAfter<Integer> lines = new BeforeAfter<Integer>(0,0);
    for (BeforeAfter<TextRange> lineNumbers : fragmentedContent.getRanges()) {
      myLineRanges.add(lines);
      oldConvertor.put(lines.getBefore(), lineNumbers.getBefore().getStartOffset());
      newConvertor.put(lines.getAfter(), lineNumbers.getAfter().getStartOffset());

      final Document document = fragmentedContent.getBefore();
      if (sbOld.length() > 0) {
        sbOld.append('\n');
      }
      final TextRange beforeRange = new TextRange(document.getLineStartOffset(lineNumbers.getBefore().getStartOffset()),
                                      document.getLineEndOffset(lineNumbers.getBefore().getEndOffset()));
      myBeforeFragments.add(beforeRange);
      sbOld.append(document.getText(beforeRange));

      final Document document1 = fragmentedContent.getAfter();
      if (sbNew.length() > 0) {
        sbNew.append('\n');
      }
      final TextRange afterRange = new TextRange(document1.getLineStartOffset(lineNumbers.getAfter().getStartOffset()),
                                      document1.getLineEndOffset(lineNumbers.getAfter().getEndOffset()));
      myAfterFragments.add(afterRange);
      sbNew.append(document1.getText(afterRange));

      int before = lines.getBefore() + lineNumbers.getBefore().getEndOffset() - lineNumbers.getBefore().getStartOffset() + 1;
      int after = lines.getAfter() + lineNumbers.getAfter().getEndOffset() - lineNumbers.getAfter().getStartOffset() + 1;
      lines = new BeforeAfter<Integer>(before, after);
    }
    myLineRanges.add(new BeforeAfter<Integer>(lines.getBefore() == 0 ? 0 : lines.getBefore() - 1,
                                              lines.getAfter() == 0 ? 0 : lines.getAfter() - 1));
  }

  public LineNumberConvertor getOldConvertor() {
    return oldConvertor;
  }

  public LineNumberConvertor getNewConvertor() {
    return newConvertor;
  }

  public StringBuilder getSbOld() {
    return sbOld;
  }

  public StringBuilder getSbNew() {
    return sbNew;
  }

  public List<TextRange> getBeforeFragments() {
    return myBeforeFragments;
  }

  public List<TextRange> getAfterFragments() {
    return myAfterFragments;
  }

  public List<BeforeAfter<Integer>> getLineRanges() {
    return myLineRanges;
  }

  public boolean isOneSide() {
    return myOneSide;
  }

  public FragmentedEditorHighlighter getBeforeHighlighter() {
    return myBeforeHighlighter;
  }

  public void setBeforeHighlighter(FragmentedEditorHighlighter beforeHighlighter) {
    myBeforeHighlighter = beforeHighlighter;
  }

  public FragmentedEditorHighlighter getAfterHighlighter() {
    return myAfterHighlighter;
  }

  public void setAfterHighlighter(FragmentedEditorHighlighter afterHighlighter) {
    myAfterHighlighter = afterHighlighter;
  }

  public boolean isEmpty() {
    return myLineRanges.isEmpty();
  }
}
