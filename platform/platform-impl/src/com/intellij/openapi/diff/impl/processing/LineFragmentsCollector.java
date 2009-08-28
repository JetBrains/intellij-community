package com.intellij.openapi.diff.impl.processing;

import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.fragments.LineFragment;
import com.intellij.openapi.diff.impl.util.TextDiffType;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;

import java.util.ArrayList;

class LineFragmentsCollector {
  private final ArrayList<LineFragment> myLineFragments = new ArrayList<LineFragment>();
  private int myLine1 = 0;
  private int myLine2 = 0;
  private int myOffset1 = 0;
  private int myOffset2 = 0;

  private LineFragment addFragment(TextDiffType type, String text1, String text2) {
    int lines1 = countLines(text1);
    int lines2 = countLines(text2);
    int endOffset1 = myOffset1 + getLength(text1);
    int endOffset2 = myOffset2 + getLength(text2);
    LineFragment lineFragment = new LineFragment(myLine1, lines1, myLine2, lines2, type,
                              new TextRange(myOffset1, endOffset1),
                              new TextRange(myOffset2, endOffset2));
    myLine1 += lines1;
    myLine2 += lines2;
    myOffset1 = endOffset1;
    myOffset2 = endOffset2;
    myLineFragments.add(lineFragment);
    return lineFragment;
  }

  public LineFragment addDiffFragment(DiffFragment fragment) {
    return addFragment(getType(fragment), fragment.getText1(), fragment.getText2());
  }

  static int getLength(String text) {
    return text == null ? 0 : text.length();
  }

  private static int countLines(String text) {
    if (text == null || text.length() == 0) return 0;
    int count = StringUtil.countNewLines(text);
    if (text.charAt(text.length()-1) != '\n') count++;
    return count;
  }

  public ArrayList<LineFragment> getFragments() {
    return myLineFragments;
  }

  static TextDiffType getType(DiffFragment fragment) {
    TextDiffType type;
    if (fragment.getText1() == null) type = TextDiffType.INSERT;
    else if (fragment.getText2() == null) type = TextDiffType.DELETED;
    else if (fragment.isModified()) type = TextDiffType.CHANGED;
    else type = null;
    return type;
  }
}
