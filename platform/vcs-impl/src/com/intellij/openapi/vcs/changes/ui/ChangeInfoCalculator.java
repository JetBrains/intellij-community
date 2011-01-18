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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.util.Processor;

import java.util.Collections;
import java.util.List;

/**
* @author irengrig
*         Date: 1/14/11
*         Time: 6:36 PM
*/
public class ChangeInfoCalculator implements CommitLegendPanel.InfoCalculator {
  private List<Change> myDisplayedChanges;
  private List<Change> myIncludedChanges;

  public ChangeInfoCalculator() {
    myDisplayedChanges = Collections.emptyList();
    myIncludedChanges = Collections.emptyList();
  }

  public void update(final List<Change> displayedChanges, final List<Change> includedChanges) {
    myDisplayedChanges = displayedChanges;
    myIncludedChanges = includedChanges;
  }

  public int getNew() {
    return countMatchingItems(myDisplayedChanges, NEW_FILTER);
  }

  public int getModified() {
    return countMatchingItems(myDisplayedChanges, MODIFIED_FILTER);
  }

  public int getDeleted() {
    return countMatchingItems(myDisplayedChanges, DELETED_FILTER);
  }

  public int getIncludedNew() {
    return countMatchingItems(myIncludedChanges, NEW_FILTER);
  }

  public int getIncludedModified() {
    return countMatchingItems(myIncludedChanges, MODIFIED_FILTER);
  }

  public int getIncludedDeleted() {
    return countMatchingItems(myIncludedChanges, DELETED_FILTER);
  }

  private static final Processor<Change> MODIFIED_FILTER = new Processor<Change>() {
    public boolean process(final Change item) {
      return item.getType() == Change.Type.MODIFICATION || item.getType() == Change.Type.MOVED;
    }
  };
  private static final Processor<Change> NEW_FILTER = new Processor<Change>() {
    public boolean process(final Change item) {
      return item.getType() == Change.Type.NEW;
    }
  };
  private static final Processor<Change> DELETED_FILTER = new Processor<Change>() {
    public boolean process(final Change item) {
      return item.getType() == Change.Type.DELETED;
    }
  };

  private static <T> int countMatchingItems(List<T> items, Processor<T> filter) {
    int count = 0;
    for (T item : items) {
      if (filter.process(item)) count++;
    }

    return count;
  }
}
