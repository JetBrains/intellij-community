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

import com.intellij.execution.Location;
import com.intellij.execution.junit2.events.*;
import com.intellij.execution.junit2.info.TestInfo;
import com.intellij.execution.junit2.states.Statistics;
import com.intellij.execution.junit2.states.TestState;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Filter;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.rt.execution.junit.states.PoolOfTestStates;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class TestProxy extends AbstractTestProxy {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit2.TestProxy");

  private final TestInfo myInfo;
  private TestState myState = TestState.DEFAULT;
  private final TestProxyListenersNotifier myNotifier = new TestProxyListenersNotifier();
  private Statistics myStatistics = new Statistics();
  private TestEventsConsumer myEventsConsumer;
  private int myPreviousMagnitude = -1;
  private int myStateTimestamp = 0;

  //  private ArrayList myChildren = new ArrayList();
  private final FilterCache myChildren = new FilterCache();
  private TestProxy myParent = null;
  public static final Filter NOT_LEAF = Filter.LEAF.not();

  public TestProxy(@NotNull final TestInfo info) {
    myInfo = info;
  }

  public String toString() {
    return getInfo().getComment() + "." + getInfo().getName();
  }

  public TestState getState() {
    return myState;
  }

  public void setState(final TestState state) {
    if (myState != state) {
      if (state.isFinal()) {
        addLast(state);
      } else {
        fireOnNewPrintable(state);
      }
      if (myState != null) {
        state.merge(myState, getParent());
      }

      myState = state;
      fireStateChanged();
    }
  }

  public void fireStateChanged() {
    myStateTimestamp++;
    pullEvent(new StateChangedEvent(this));
    if (myParent != null)
      myParent.onChanged(this);
    myNotifier.onChanged(this);
  }

  public int getStateTimestamp() {
    return myStateTimestamp;
  }

  public int getChildCount() {
    return myChildren.getList().size();
  }

  public List<TestProxy> getChildren() {
    return myChildren.getUnmodifiableList();
  }

  public TestProxy getParent() {
    return myParent;
  }

  public Navigatable getDescriptor(final Location location, final TestConsoleProperties testConsoleProperties) {
    return getState().getDescriptor(location);
  }

  public String getName() {
    return getInfo().getName();
  }

  public boolean isInProgress() {
    return getState().isInProgress();
  }

  public boolean isDefect() {
    return getState().isDefect();
  }

  public boolean shouldRun() {
    return getInfo().shouldRun();
  }

  public int getMagnitude() {
    return getState().getMagnitude();
  }

  public Location getLocation(final Project project) {
    return getInfo().getLocation(project);
  }

  public boolean isLeaf() {
    return getChildCount() == 0;
  }

  @Override
  public boolean isInterrupted() {
    return getMagnitude() == PoolOfTestStates.TERMINATED_INDEX;
  }

  @Override
  public boolean isIgnored() {
    return getMagnitude() == PoolOfTestStates.IGNORED_INDEX;
  }

  public boolean isPassed() {
    return getMagnitude() <= PoolOfTestStates.PASSED_INDEX;
  }

  public void addChild(final TestProxy child) {
    if (myChildren.contains(child))
      return;
    if (child.getParent() != null)
      return;//todo throw new RuntimeException("Test: "+child + " already has parent: " + child.getParent());
    myChildren.add(child);
    child.myParent = this;
    addLast(child);
    child.setPrinter(myPrinter);
    pullEvent(new NewChildEvent(this, child));
    getState().changeStateAfterAddingChildTo(this, child);
    myNotifier.onChildAdded(this, child);
  }


  public TestInfo getInfo() {
    return myInfo;
  }


  public void onChanged(final AbstractTestProxy test) {
    myChildren.resetCache();
    final int magnitude = test.getMagnitude();
    getState().update();
    if (myPreviousMagnitude < magnitude || getState().getMagnitude() <= magnitude) {
      fireStateChanged();
      myPreviousMagnitude = getState().getMagnitude();
    }
  }

  public void onStatisticsChanged() {
    myChildren.resetCache();
    if (myParent != null)
      myParent.onStatisticsChanged();
    pullEvent(new StatisticsChanged(this));
    myNotifier.onStatisticsChanged(this);
  }

  public void addListener(final TestProxyListener listener) {
    myNotifier.addListener(listener);
  }

  public void setStatistics(final Statistics statistics) {
    if (!myState.isFinal()) {
      LOG.error(String.valueOf(myState.getMagnitude()));
    }
    myStatistics = statistics;
    onStatisticsChanged();
  }

  public Statistics getStatisticsImpl() {
    return myStatistics;
  }

  public boolean hasChildSuites() {
    return myChildren.detect(NOT_LEAF) != null;
  }

  public Statistics getStatistics() {
    return myState.getStatisticsFor(this);
  }

  public TestProxy[] selectChildren(final Filter filter) {
    return myChildren.select(filter);
  }

  public void setEventsConsumer(final TestEventsConsumer eventsConsumer) {
    myEventsConsumer = eventsConsumer;
  }

  private void pullEvent(final TestEvent event) {
    if (myEventsConsumer != null) {
      myEventsConsumer.onEvent(event);
      return;
    }
    if (myParent != null)
      myParent.pullEvent(event);
  }

  public List<TestProxy> getAllTests() {
    return myState.getAllTestsOf(this);
  }

  public void collectAllTestsTo(final ArrayList<TestProxy> allTests) {
    allTests.add(this);
    for (Iterator iterator = myChildren.iterator(); iterator.hasNext();) {
      final TestProxy testProxy = (TestProxy) iterator.next();
      testProxy.collectAllTestsTo(allTests);
    }
  }

  public TestProxy getCommonAncestor(final TestProxy test) {
    if (test == null) return this;
    if (test.isAncestorOf(this)) return test;
    for (TestProxy parent = this; parent != null; parent = parent.getParent())
      if (parent.isAncestorOf(test)) return parent;
    return null;
  }

  public boolean isAncestorOf(final TestProxy test) {
    if (test == null) return false;
    for (TestProxy parent = test; parent != null; parent = parent.getParent())
      if (parent == this) return true;
    return false;
  }

  public AbstractTestProxy[] getPathFromRoot() {
    final ArrayList<TestProxy> parents = new ArrayList<TestProxy>();
    TestProxy test = this;
    do {
      parents.add(test);
    } while ((test = test.getParent()) != null);
    Collections.reverse(parents);
    return parents.toArray(new TestProxy[parents.size()]);
  }

  @Override
  public Integer getDuration() {
    return getStatistics().getTime();
  }

  @Override
  public boolean shouldSkipRootNodeForExport() {
    return false;
  }

  @Override
  @Nullable
  public AssertEqualsDiffViewerProvider getDiffViewerProvider() {
    if (myState instanceof AssertEqualsDiffViewerProvider) {
      return (AssertEqualsDiffViewerProvider)myState;
    }
    return null;
  }
}
