/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.pullrequests.util;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

public class GithubLoadMoreListModel<T> extends AbstractListModel {
  public static final Object SHOW_MORE_ELEMENT = new Object();

  @NotNull private List<T> myElements = Collections.emptyList();
  private boolean myCanShowMore;

  @Override
  public int getSize() {
    return myCanShowMore ? myElements.size() + 1 : myElements.size();
  }

  @Override
  public Object getElementAt(int index) {
    if (myCanShowMore && index == getSize() - 1) {
      return SHOW_MORE_ELEMENT;
    }
    else {
      return index < myElements.size() ? myElements.get(index) : null;
    }
  }

  public void setElements(@NotNull List<T> elements, boolean canShowMore) {
    fireIntervalRemoved(this, 0, getSize());
    myElements = elements;
    myCanShowMore = canShowMore;
    fireIntervalAdded(this, 0, getSize());
  }

  @NotNull
  public List<T> getElements() {
    return myElements;
  }

  public boolean canShowMore() {
    return myCanShowMore;
  }
}
