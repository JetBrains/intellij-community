package com.intellij.execution.junit2.ui.model;

import com.intellij.execution.junit2.TestEvent;
import com.intellij.execution.junit2.TestEventsConsumer;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.segments.DispatchListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;

import java.util.*;

public class JUnitListenersNotifier implements JUnitListener, TestEventsConsumer, DispatchListener, Runnable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit2.ui.model.JUnitListenersNotifier");

  private final ArrayList<JUnitListener> myListeners = new ArrayList<JUnitListener>();
  private final ArrayList<TestEvent> myEventsQueue = new ArrayList<TestEvent>();
  private final Flag myCollectingEvents = new Flag(LOG, true);
  private final Alarm myAlarm = new Alarm();
  private final boolean myDeferEvents;
//  public static TimeMeasurer MEASURER = new TimeMeasurer();
//  private static final String ON_TEST_SELECTED = "TestSelected";
//  private static final String DISPATCH_SINGLES = "Dispatch single events";
//  private static final String PACKET_DISPATCH = "DispatchPacket";
//  private static final String ON_EVENT = "onEvent";

  public JUnitListenersNotifier(final boolean deferEvents) {
    myDeferEvents = deferEvents;
//    MEASURER.reset();
  }

  public JUnitListenersNotifier() {
    this(!ApplicationManager.getApplication().isUnitTestMode());
  }

  public void onTestSelected(final TestProxy test) {
//    MEASURER.start(ON_TEST_SELECTED);
    final JUnitListener[] listeners = getListeners();
    for (final JUnitListener listener : listeners) {
      listener.onTestSelected(test);
    }
//    MEASURER.stop(ON_TEST_SELECTED);
  }

  public void onDispose(final JUnitRunningModel model) {
    final JUnitListener[] listeners = getListeners();
    for (final JUnitListener listener : listeners) {
      listener.onDispose(model);
    }
  }

  public void onTestChanged(final TestEvent event) {
    LOG.assertTrue(false);
  }

  private void dispatchTestEvent(final TestEvent event) {
    final JUnitListener[] listeners = getListeners();
    for (final JUnitListener listener : listeners) {
      listener.onTestChanged(event);
    }
  }

  public void onRunnerStateChanged(final StateEvent event) {
//    if (!event.isRunning()) MEASURER.printAll();
    final JUnitListener[] listeners = getListeners();
    for (final JUnitListener listener : listeners) {
      listener.onRunnerStateChanged(event);
    }
    if (!myCollectingEvents.getValue()) {
      myAlarm.cancelAllRequests();
      dispatchAllEvents();
    }
  }

  public void onEventsDispatched(final List<TestEvent> events) {
//    MEASURER.start(PACKET_DISPATCH);
    final JUnitListener[] listeners = getListeners();
    for (final JUnitListener listener : listeners) {
      listener.onEventsDispatched(events);
    }
//    MEASURER.stop(PACKET_DISPATCH);
  }

  private JUnitListener[] getListeners() {
    return myListeners.toArray(new JUnitListener[myListeners.size()]);
  }

  public void onEvent(final TestEvent event) {
//    MEASURER.start(ON_EVENT);
    if (myEventsQueue.isEmpty() && myDeferEvents) {
      myAlarm.cancelAllRequests();
      myAlarm.addRequest(this, 400);
    }
    myEventsQueue.add(event);
//    MEASURER.stop(ON_EVENT);
  }

  public void addListener(final JUnitListener listener) {
    LOG.assertTrue(listener != null);
    myListeners.add(listener);
  }

  public void removeListener(final JUnitListener listener) {
    myListeners.remove(listener);
  }

  public void onStarted() {
    myCollectingEvents.assertValue(false);
    myCollectingEvents.setValue(true);
  }

  public void onFinished() {
    myCollectingEvents.assertValue(true);
    myCollectingEvents.setValue(false);
    if (!myDeferEvents)
      run();
  }

  public void run() {
    myCollectingEvents.assertValue(false);
    dispatchAllEvents();
  }

  private void dispatchAllEvents() {
    //long start = System.currentTimeMillis();
    final List<TestEvent> filteredEvents = removeDuplicatesFromEnd(myEventsQueue);
    myEventsQueue.clear();
//    MEASURER.start(DISPATCH_SINGLES);
    for (final TestEvent event : filteredEvents) {
      dispatchTestEvent(event);
    }
//    MEASURER.stop(DISPATCH_SINGLES);
    onEventsDispatched(filteredEvents);
    //System.out.println("duration = " + (System.currentTimeMillis() - start));
  }

  public static <T> List<T> removeDuplicatesFromEnd(final List<T> list) {
    ArrayList<T> result = new ArrayList<T>(list);
    Collections.reverse(result);
    ContainerUtil.removeDuplicates(result);
    Collections.reverse(result);
    return result;
  }
}
