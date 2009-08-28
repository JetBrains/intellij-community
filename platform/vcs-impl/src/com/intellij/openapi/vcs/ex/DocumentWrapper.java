package com.intellij.openapi.vcs.ex;

import com.intellij.openapi.editor.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * author: lesya
 */
public class DocumentWrapper {
  private final Document myDocument;

  public DocumentWrapper(Document document) {
    myDocument = document;
  }

  public int getLineNum(int offset) {
    return myDocument.getLineNumber(offset);
  }

  public List<String> getLines() {
    return getLines(0, myDocument.getLineCount() - 1);
  }

  public List<String> getLines(int from, int to) {
    ArrayList<String> result = new ArrayList<String>();
    for (int i = from; i <= to; i++) {
      if (i >= myDocument.getLineCount()) break;
      final String line = getLine(i);
      /*
      if (line.length() > 0 || i < to) {
        result.add(line);
      }
      */
      result.add(line);
    }
    return result;
  }

  private String getLine(int i) {
    int lineStartOffset = myDocument.getLineStartOffset(i);
    String line = myDocument.getCharsSequence().subSequence(lineStartOffset, myDocument.getLineEndOffset(i)).toString();
    return line;
  }
}

