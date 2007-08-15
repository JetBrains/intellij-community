package com.intellij.execution.junit2;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Filter;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class FilterCache {
  private final ArrayList<TestProxy> myList = new ArrayList<TestProxy>(4);
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

  public void add(final TestProxy test) {
    myList.add(test);
    resetCache();
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
