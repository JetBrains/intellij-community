package com.intellij.openapi.vcs.ex;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.util.diff.Diff;

import java.util.ArrayList;
import java.util.List;

/**
 * author: lesya
 */

public class RangesBuilder {
  private List<Range> myRanges;

  public RangesBuilder(String current, String upToDate) {
    this(EditorFactory.getInstance().createDocument(current), EditorFactory.getInstance().createDocument(upToDate));
  }

  public RangesBuilder(Document current, Document upToDate) {
    this(new DocumentWrapper(current).getLines(), new DocumentWrapper(upToDate).getLines(), 0, 0);
  }

  public RangesBuilder(List<String> current, List<String> upToDate, int shift, int uShift) {
    myRanges = new ArrayList<Range>();

    int shiftBefore = 0;

    int minSize = Math.min(upToDate.size(), current.size());

    for (int i = 0; i < minSize; i++) {
      if (upToDate.get(0).equals(current.get(0))) {
        upToDate.remove(0);
        current.remove(0);
        shiftBefore += 1;
      }
      else {
        break;
      }
    }

    minSize = Math.min(upToDate.size(), current.size());

    for (int i = 0; i < minSize; i++) {
      if (upToDate.get(upToDate.size() - 1).equals(current.get(current.size() - 1))) {
        upToDate.remove(upToDate.size() - 1);
        current.remove(current.size() - 1);
      }
      else {
        break;
      }
    }

    Diff.Change ch = Diff.buildChanges(upToDate.toArray(new String[upToDate.size()]), current.toArray(new String[current.size()]));


    while (ch != null) {
      Range range = Range.createOn(ch, shift + shiftBefore, uShift + shiftBefore);
      myRanges.add(range);
      ch = ch.link;
    }

  }

  public List<Range> getRanges() {
    return myRanges;
  }

}
