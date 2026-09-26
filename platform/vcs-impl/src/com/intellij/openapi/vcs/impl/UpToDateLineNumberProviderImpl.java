// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import org.jetbrains.annotations.Nullable;

public class UpToDateLineNumberProviderImpl implements UpToDateLineNumberProvider {
  private final Document myDocument;
  private final LineStatusTrackerManagerI myLineStatusTrackerManagerI;

  public UpToDateLineNumberProviderImpl(Document document, Project project) {
    myDocument = document;
    myLineStatusTrackerManagerI = LineStatusTrackerManager.getInstance(project);
  }

  @Override
  public boolean isRangeChanged(final int start, final int end) {
    LineStatusTracker<?> tracker = getTracker();
    if (tracker == null) {
      return false;
    }
    else {
      return tracker.isRangeModified(start, end);
    }
  }

  @Override
  public boolean isLineChanged(int currentNumber) {
    LineStatusTracker<?> tracker = getTracker();
    if (tracker == null) {
      return false;
    }
    else {
      return tracker.isLineModified(currentNumber);
    }
  }

  @Override
  public int getLineNumber(int currentNumber) {
    return getLineNumber(currentNumber, false);
  }

  @Override
  public int getLineNumber(int currentNumber, boolean approximate) {
    LineStatusTracker<?> tracker = getTracker();
    if (tracker == null) {
      return currentNumber;
    }
    else {
      return tracker.transferLineToVcs(currentNumber, approximate);
    }
  }

  @Override
  public int getLineCount() {
    LineStatusTracker<?> tracker = getTracker();
    if (tracker == null) {
      return myDocument.getLineCount();
    }
    else {
      return tracker.getVcsDocument().getLineCount();
    }
  }

  private @Nullable LineStatusTracker<?> getTracker() {
    LineStatusTracker<?> tracker = myLineStatusTrackerManagerI.getLineStatusTracker(myDocument);
    return tracker != null && tracker.isOperational() ? tracker : null;
  }
}
