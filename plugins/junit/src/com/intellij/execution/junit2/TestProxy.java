package com.intellij.execution.junit2;

import com.intellij.execution.Location;
import com.intellij.execution.junit2.info.TestInfo;
import com.intellij.execution.junit2.segments.InputConsumer;
import com.intellij.execution.junit2.states.Statistics;
import com.intellij.execution.junit2.states.TestState;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Filter;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class TestProxy extends CompositePrintable implements AbstractTestProxy, InputConsumer, ChangingPrintable, TestProxyListener, TestProxyParent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit2.TestProxy");
  public static final String NEW_LINE = "\n";

  private final TestInfo myInfo;
  private TestState myState = TestState.DEFAULT;
  private Printer myPrinter = Printer.DEAF;
  private final TestProxyListenersNotifier myNotifier = new TestProxyListenersNotifier();
  private Statistics myStatistics = new Statistics();
  private TestEventsConsumer myEventsConsumer;
  private int myPreviousMagnitude = -1;
  private int myStateTimestamp = 0;

//  private ArrayList myChildren = new ArrayList();
  private final FilterCache myChildren = new FilterCache();
  private TestProxy myParent = null;

  public TestProxy(@NotNull final TestInfo info) {
    myInfo = info;
  }

  public String toString() {
    return getInfo().getComment() + "." + getInfo().getName();
  }

  public void onOutput(final String text, final ConsoleViewContentType contentType) {
    final ExternalOutput printable = new ExternalOutput(text, contentType);
    addLast(printable);
  }

  public void addLast(final Printable printable) {
    super.addLast(printable);
    fireOnNewPrintable(printable);
  }

  private void fireOnNewPrintable(final Printable printable) {
    myPrinter.onNewAvaliable(printable);
  }

  public void printOn(final Printer printer) {
    super.printOn(printer);
    CompositePrintable.printAllOn(myChildren.getList(), printer);
    myState.printOn(printer);
  }

  public TestState getState() {
    return myState;
  }

  public void setState(final TestState state) {
    if (myState != state) {
      myState = state;
      fireOnNewPrintable(state);
      fireStateChanged();
    }
  }

  private void fireStateChanged() {
    myStateTimestamp++;
    pullEvent(new StateChangedEvent(this));
    if (myParent != null)
      myParent.onChanged(this);
    fireStatisticsChanged();
    myNotifier.onChanged(this);
  }

  public int getStateTimestamp() {
    return myStateTimestamp;
  }

  public TestProxy getChildAt(final int childIndex) {
    return myChildren.getList().get(childIndex);
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

  public Navigatable getDescriptor(final Location location) {
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

  public void addChild(final TestProxy child) {
    if (myChildren.getList().contains(child))
      return;
    if (child.getParent() != null)
      throw new RuntimeException("Test: "+child + " already has parent: " + child.getParent());
    myChildren.add(child);
    child.myParent = this;
    if (myPrinter != Printer.DEAF) {
      child.setPrintLinstener(myPrinter);
      child.fireOnNewPrintable(child);
    }
    pullEvent(new NewChildEvent(this, child));
    fireStatisticsChanged();
    getState().changeStateAfterAddingChaildTo(this, child);
    myNotifier.onChildAdded(this, child);
  }

  public void setPrintLinstener(final Printer printer) {
    myPrinter = printer;
    for (Iterator iterator = myChildren.iterator(); iterator.hasNext();) {
      final TestProxy testProxy = (TestProxy) iterator.next();
      testProxy.setPrintLinstener(printer);
    }
  }

  public TestInfo getInfo() {
    return myInfo;
  }

  public void onChildAdded(final AbstractTestProxy parent, final AbstractTestProxy newChild) {
    fireStatisticsChanged();
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

  public void onStatisticsChanged(final AbstractTestProxy testProxy) {
    myChildren.resetCache();
    fireStatisticsChanged();
  }

  private void fireStatisticsChanged() {
    myChildren.resetCache();
    if (myParent != null)
      myParent.onStatisticsChanged(this);
    pullEvent(new StatisticsChanged(this));
    myNotifier.onStatisticsChanged(this);
  }

  public void addListener(final TestProxyListener listener) {
    myNotifier.addListener(listener);
  }

  public void setStatistics(final Statistics statistics) {
    myChildren.resetCache();
    if (!myState.isFinal()) {
      LOG.error("" + myState.getMagnitude());
    }
    myStatistics = statistics;
    fireStatisticsChanged();
  }

  public Statistics getStatisticsImpl() {
    return myStatistics;
  }

  public boolean hasChildSuites() {
    return myChildren.detect(Filter.NOT_LEAF) != null;
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

  public boolean isRoot() {
    return getParent() == null;
  }

  public static AbstractTestProxy fromDataContext(final DataContext dataContext) {
    return (AbstractTestProxy)dataContext.getData(DATA_CONSTANT);
  }

  public TestState.StateInterval calculateInterval(final SuiteState state) {
    final SuiteState.SuiteStateInterval result = new SuiteState.SuiteStateInterval(state, getChildAt(0).getState().getInterval());
    for (int i = 0; i < getChildCount(); i++) {
      final TestState.StateInterval interval = getChildAt(i).getState().getInterval();
      result.updateFrom(interval);
    }
    return result;
  }
}
