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

/**
 * author: lesya
 */
public class UpToDateLineNumberProviderImpl implements UpToDateLineNumberProvider {
  private final Document myDocument;
  private final LineStatusTrackerManagerI myLineStatusTrackerManagerI;

  public UpToDateLineNumberProviderImpl(Document document, Project project) {
    myDocument = document;
    myLineStatusTrackerManagerI = LineStatusTrackerManager.getInstance(project);
  }

  @Override
  public boolean isRangeChanged(final int start, final int end) {
    LineStatusTracker tracker = myLineStatusTrackerManagerI.getLineStatusTracker(myDocument);
    if (tracker == null || !tracker.isOperational()) {
      return false;
    }
    return tracker.isRangeModified(start, end);
  }

  @Override
  public boolean isLineChanged(int currentNumber) {
    LineStatusTracker tracker = myLineStatusTrackerManagerI.getLineStatusTracker(myDocument);
    if (tracker == null || !tracker.isOperational()) {
      return false;
    }
    return tracker.isLineModified(currentNumber);
  }

  @Override
  public int getLineNumber(int currentNumber) {
    LineStatusTracker tracker = myLineStatusTrackerManagerI.getLineStatusTracker(myDocument);
    if (tracker == null || !tracker.isOperational()) {
      return currentNumber;
    }
    return tracker.transferLineToVcs(currentNumber, false);
  }
}
