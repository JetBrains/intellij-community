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
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * author: lesya
 */
public class UpToDateLineNumberProviderImpl implements UpToDateLineNumberProvider {
  private final Document myDocument;
  private final Project myProject;
  private final LineStatusTrackerManagerI myLineStatusTrackerManagerI;

  public UpToDateLineNumberProviderImpl(Document document, Project project) {
    myDocument = document;
    myProject = project;
    myLineStatusTrackerManagerI = LineStatusTrackerManager.getInstance(myProject);
  }

  public int getLineNumber(int currentNumber) {
    LineStatusTracker tracker = myLineStatusTrackerManagerI.getLineStatusTracker(myDocument);
    if (tracker == null) {
      return currentNumber;
    }
    return calcLineNumber(tracker, currentNumber);
  }

  public boolean isRangeChanged(final int start, final int end) {
    LineStatusTracker tracker = LineStatusTrackerManager.getInstance(myProject).getLineStatusTracker(myDocument);
    if (tracker == null) {
      return false;
    }
    for (Range range : tracker.getRanges()) {
      if (lineInRange(range, start) || lineInRange(range, end)) {
        return true;
      }
      if (range.getLine1() > start) {
        return range.getLine1() < end;
      }
    }
    return false;
  }

  private static boolean lineInRange(final Range range, final int currentNumber) {
    return range.getLine1() <= currentNumber && range.getLine2() >= currentNumber;
  }

  @Override
  public boolean isLineChanged(int currentNumber) {
    LineStatusTracker tracker = LineStatusTrackerManager.getInstance(myProject).getLineStatusTracker(myDocument);
    if (tracker == null) {
      return false;
    }
    for (Range range : tracker.getRanges()) {
      if (range.getLine1() <= currentNumber && range.getLine2() >= currentNumber) {
        return true;
      }
    }
    return false;
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

  private static int calcLineNumber(@NotNull LineStatusTracker tracker, int currentNumber) {
    List<Range> ranges = tracker.getRanges();
    int result = currentNumber;

    for (final Range range : ranges) {
      int startLine = range.getLine1();
      int endLine = range.getLine2();

      if ((startLine <= currentNumber) && (endLine > currentNumber)) {
        return ABSENT_LINE_NUMBER;
      }

      if (endLine > currentNumber) return result;

      int currentRangeLength = endLine - startLine;
      int vcsRangeLength = range.getVcsLine2() - range.getVcsLine1();

      result += vcsRangeLength - currentRangeLength;
    }
    return result;

  }

}


