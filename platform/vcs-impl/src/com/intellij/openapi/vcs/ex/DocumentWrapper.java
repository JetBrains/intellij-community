/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.ex;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;

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

  private String getLine(final int i) {
    TextRange range = new TextRange(myDocument.getLineStartOffset(i), myDocument.getLineEndOffset(i));
    if (range.getLength() < 0) {
      assert false : myDocument;
    }
    return myDocument.getText(range);
  }
}

