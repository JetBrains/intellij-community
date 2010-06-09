/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import java.util.HashSet;
import java.util.Set;

public class QuantitySelection<T> implements SelectionManipulation<T>, SelectionState<T> {
  private final Group<T> mySelected;
  private final Group<T> myUnselected;

  public QuantitySelection(final boolean startFromSelectAll) {
    mySelected = new Group<T>();
    myUnselected = new Group<T>();
    if (startFromSelectAll) {
      mySelected.setAll();
    } else {
      myUnselected.setAll();
    }
  }

  public void add(T t) {
    if (mySelected.isAll()) {
      myUnselected.remove(t);
    } else {
      mySelected.add(t);
    }
  }

  public void remove(T t) {
    if (mySelected.isAll()) {
      myUnselected.add(t);
    } else {
      mySelected.remove(t);
    }
  }

  public void clearAll() {
    mySelected.clearAll();
    myUnselected.setAll();
  }

  public void setAll() {
    myUnselected.clearAll();
    mySelected.setAll();
  }

  public SelectionResult<T> getSelected() {
    return mySelected;
  }

  public SelectionResult<T> getUnselected() {
    return myUnselected;
  }

  public boolean isSelected(final T t) {
    return mySelected.isAll() && (! myUnselected.hasPoint(t)) || myUnselected.isAll() && mySelected.hasPoint(t);
  }

  public static class Group<T> implements SelectionManipulation<T>, SelectionResult<T> {
    private boolean myAll;
    private final Set<T> myMarked;

    private Group() {
      myMarked = new HashSet<T>();
    }

    public void add(final T t) {
      myMarked.add(t);
    }

    // +- consistency check not here
    public void remove(final T t) {
      myAll = false;
      myMarked.remove(t);
    }

    public void clearAll() {
      myAll = false;
      myMarked.clear();
    }

    public void setAll() {
      myAll = true;
      myMarked.clear();
    }

    public Set<T> getMarked() {
      return myMarked;
    }

    public boolean isAll() {
      return myAll;
    }

    public boolean hasPoint(final T t) {
      return myMarked.contains(t);
    }

    public boolean isIncluded(final T t) {
      return myAll || myMarked.contains(t);
    }
  }
}
