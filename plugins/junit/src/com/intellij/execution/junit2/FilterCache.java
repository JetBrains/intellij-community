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

package com.intellij.execution.junit2;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Filter;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class FilterCache {
  private final ArrayList<TestProxy> myList = new ArrayList<TestProxy>(4);
  private final Set<TestProxy> mySet = new HashSet<TestProxy>();
  private final Map<Filter, ArrayList<TestProxy>> myCache = new THashMap<Filter, ArrayList<TestProxy>>();

  public TestProxy[] select(final Filter filter) {
    final ArrayList<TestProxy> selected = selectImpl(filter);
    return selected.toArray(new TestProxy[selected.size()]);
  }

  public List<TestProxy> getList() {
    return myList;
  }

  public void resetCache() {
    myCache.clear();
  }

  public List<TestProxy> getUnmodifiableList() {
    return Collections.unmodifiableList(myList);
  }

  public void insert(TestProxy child, int idx) {
    if (idx >= 0) {
      myList.add(idx, child);
    } else {
      myList.add(child);
    }
    mySet.add(child);
    resetCache();
  }

  public boolean contains(TestProxy test) {
    return mySet.contains(test);
  }

  public AbstractTestProxy detect(final Filter filter) {
    return filter.detectIn(myList);
  }

  public Iterator iterator() {
    return myList.iterator();
  }

  private ArrayList<TestProxy> selectImpl(@NotNull final Filter filter) {
    final ArrayList<TestProxy> result =  myCache.get(filter);
    if (result != null) return result;
    final ArrayList<TestProxy> selected = new ArrayList<TestProxy>();
    for (final TestProxy childTest : myList) {
      if (filter.shouldAccept(childTest)) selected.add(childTest);
    }
    selected.trimToSize();
    myCache.put(filter, selected);
    return selected;
  }

}
