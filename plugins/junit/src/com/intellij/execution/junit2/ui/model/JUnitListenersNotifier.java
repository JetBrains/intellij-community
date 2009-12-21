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

package com.intellij.execution.junit2.ui.model;

import com.intellij.execution.junit2.events.TestEvent;
import com.intellij.execution.junit2.events.TestEventsConsumer;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.segments.DispatchListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JUnitListenersNotifier implements TestEventsConsumer, DispatchListener, Runnable {
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

  public void fireTestSelected(final TestProxy test) {
//    MEASURER.start(ON_TEST_SELECTED);
    final JUnitListener[] listeners = getListeners();
    for (final JUnitListener listener : listeners) {
      listener.onTestSelected(test);
    }
//    MEASURER.stop(ON_TEST_SELECTED);
  }

  public void fireDisposed(final JUnitRunningModel model) {
    final JUnitListener[] listeners = getListeners();
    for (final JUnitListener listener : listeners) {
      listener.onDispose(model);
    }
  }

  private void dispatchTestEvent(final TestEvent event) {
    final JUnitListener[] listeners = getListeners();
    for (final JUnitListener listener : listeners) {
      listener.onTestChanged(event);
    }
  }

  public void fireRunnerStateChanged(final StateEvent event) {
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

  public void fireEventsDispatched(final List<TestEvent> events) {
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

  public void addListener(@NotNull JUnitListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(final JUnitListener listener) {
    myListeners.remove(listener);
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
    final List<TestEvent> filteredEvents = removeDuplicates(myEventsQueue);
    myEventsQueue.clear();
//    MEASURER.start(DISPATCH_SINGLES);
    for (final TestEvent event : filteredEvents) {
      dispatchTestEvent(event);
    }
//    MEASURER.stop(DISPATCH_SINGLES);
    fireEventsDispatched(filteredEvents);
    //System.out.println("duration = " + (System.currentTimeMillis() - start));
  }

  private static <T> List<T> removeDuplicates(final List<T> list) {
    final ArrayList<T> result = new ArrayList<T>(list.size());
    final Set<T> collected = new HashSet<T>();
    for (T t : list) {
      if (collected.contains(t)) continue;
      collected.add(t);
      result.add(t);
    }
    return result;
  }
}
