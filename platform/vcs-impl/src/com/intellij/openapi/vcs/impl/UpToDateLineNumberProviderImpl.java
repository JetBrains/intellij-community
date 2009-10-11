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
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.ex.Range;

import java.util.List;

/**
 * author: lesya
 */
public class UpToDateLineNumberProviderImpl implements UpToDateLineNumberProvider {
  private final Document myDocument;
  private final Project myProject;
  private final String myUpToDateContent;

  public UpToDateLineNumberProviderImpl(Document document, Project project, String upToDateContent) {
    myDocument = document;
    myProject = project;
    myUpToDateContent = upToDateContent;
  }

  public int getLineNumber(int currentNumber) {
    LineStatusTracker tracker = LineStatusTrackerManager.getInstance(myProject).getLineStatusTracker(myDocument);
    if (tracker == null) {
      tracker = LineStatusTrackerManager.getInstance(myProject).setUpToDateContent(myDocument, myUpToDateContent);
    }
    return calcLineNumber(tracker, currentNumber);
  }

  private boolean endsWithSeparator(final CharSequence string) {
    if ((string == null) || (string.length() == 0)) {
      return false;
    }
    final char latest = string.charAt(string.length() - 1);
    return ('\n' == latest) || ('\r' == latest);
  }

  // annotated content is not aware about latest line ends with end-line separator or not. ignore latest separator difference then
  private String fixLatestLineSeparator(final Document document, final String content) {
    final CharSequence documentSequence = document.getCharsSequence();
    if (endsWithSeparator(documentSequence) && (! endsWithSeparator(content))) {
      int numCharsToCopy = 1;
      final int docLen = documentSequence.length();
      if (docLen > 1) {
        final char beforeLatest = documentSequence.charAt(docLen - 2);
        if (('\r' == beforeLatest) || ('\n' == beforeLatest)) {
          numCharsToCopy = 2;
        }
      }
      return content + documentSequence.subSequence(docLen - numCharsToCopy, docLen);
    }
    return content;
  }

  private static int calcLineNumber(LineStatusTracker tracker, int currentNumber){
    if (tracker == null) return -1;
    List ranges = tracker.getRanges();
    int result = currentNumber;

    for (final Object range1 : ranges) {
      Range range = (Range)range1;
      int startOffset = range.getOffset1();
      int endOffset = range.getOffset2();

      if ((startOffset <= currentNumber) && (endOffset > currentNumber)) {
        return ABSENT_LINE_NUMBER;
      }

      if (endOffset > currentNumber) return result;

      int currentRangeLength = endOffset - startOffset;

      result += range.getUpToDateRangeLength() - currentRangeLength;
    }
    return result;

  }

}


