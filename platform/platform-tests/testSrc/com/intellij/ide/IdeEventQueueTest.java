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
package com.intellij.ide;

import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.InvocationEvent;
import java.awt.event.KeyEvent;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class IdeEventQueueTest extends LightPlatformTestCase {
  public void testManyEventsStress() {
    int N = 100000;
    PlatformTestUtil.startPerformanceTest("Event queue dispatch", 10000, () -> {
      UIUtil.dispatchAllInvocationEvents();
      AtomicInteger count = new AtomicInteger();
      for (int i = 0; i < N; i++) {
        SwingUtilities.invokeLater(count::incrementAndGet);
      }
      UIUtil.dispatchAllInvocationEvents();
      assertEquals(N, count.get());
    }).assertTiming();
  }

  public void testKeyboardEventsAreDetected() throws InterruptedException {
    assertTrue(EventQueue.isDispatchThread());
    IdeEventQueue ideEventQueue = IdeEventQueue.getInstance();
    assertSame(ideEventQueue, Toolkit.getDefaultToolkit().getSystemEventQueue());
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    Set<AWTEvent> isDispatched = new THashSet<>();
    ideEventQueue.addDispatcher(e -> {
      isDispatched.add(e);
      LOG.debug("dispatch: "+e);
      return false;
    }, getTestRootDisposable());
    ideEventQueue.addPostprocessor(e -> {
      LOG.debug("post dispatch: "+e);
      return false;
    }, getTestRootDisposable());
    ideEventQueue.addPostEventListener(e -> {
      LOG.debug("post event hook: "+e);
      return false;
    }, getTestRootDisposable());

    int posted = ideEventQueue.myKeyboardEventsPosted.get();
    int dispatched = ideEventQueue.myKeyboardEventsDispatched.get();
    KeyEvent pressX = new KeyEvent(new JLabel("mykeypress"), KeyEvent.KEY_PRESSED, 1, InputEvent.ALT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, 11, 'x');
    postCarefully(pressX);
    assertEquals(posted+1, ideEventQueue.myKeyboardEventsPosted.get());
    assertEquals(dispatched, ideEventQueue.myKeyboardEventsDispatched.get());
    dispatchAllInvocationEventsUntilOtherEvent(ideEventQueue);
    // either it's dispatched by this method or the f*@$ing VCSRefresh activity stomped in, started modal progress and consumed all events via IdeEventQueue.pumpEventsForHierarchy
    assertTrue(isDispatched.contains(pressX) || isConsumed(pressX));

    assertEquals(posted+1, ideEventQueue.myKeyboardEventsPosted.get());
    assertEquals(dispatched+1, ideEventQueue.myKeyboardEventsDispatched.get());

    // do not react to other events
    AWTEvent ev2 = new ActionEvent(new JLabel(), ActionEvent.ACTION_PERFORMED, "myCommand");
    postCarefully(ev2);

    assertEquals(posted+1, ideEventQueue.myKeyboardEventsPosted.get());
    assertEquals(dispatched+1, ideEventQueue.myKeyboardEventsDispatched.get());
    dispatchAllInvocationEventsUntilOtherEvent(ideEventQueue);
    // either it's dispatched by this method or the f*@$ing VCSRefresh activity stomped in, started modal progress and dispatched all events via IdeEventQueue.pumpEventsForHierarchy by itself
    assertTrue(isDispatched.contains(ev2));

    assertEquals(posted+1, ideEventQueue.myKeyboardEventsPosted.get());
    assertEquals(dispatched+1, ideEventQueue.myKeyboardEventsDispatched.get());

    KeyEvent keyRelease = new KeyEvent(new JLabel("mykeyrelease"), KeyEvent.KEY_RELEASED, 1, InputEvent.ALT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, 11, 'x');
    postCarefully(keyRelease);

    assertEquals(posted+2, ideEventQueue.myKeyboardEventsPosted.get());
    assertEquals(dispatched+1, ideEventQueue.myKeyboardEventsDispatched.get());

    dispatchAllInvocationEventsUntilOtherEvent(ideEventQueue);
    // either it's dispatched by this method or the f*@$ing VCSRefresh activity stomped in, started modal progress and consumed all events via IdeEventQueue.pumpEventsForHierarchy
    assertTrue(isDispatched.contains(keyRelease) || isConsumed(keyRelease));

    assertEquals(posted+2, ideEventQueue.myKeyboardEventsPosted.get());
    assertEquals(dispatched+2, ideEventQueue.myKeyboardEventsDispatched.get());
  }

  private static void postCarefully(AWTEvent event) {
    LOG.debug("posting " + event);
    IdeEventQueue ideEventQueue = IdeEventQueue.getInstance();
    boolean posted = ideEventQueue.doPostEvent(event);
    assertTrue("Was not posted: "+event, posted);
    boolean mustBeConsumed = event.getID() == ActionEvent.ACTION_PERFORMED;
    assertEquals(mustBeConsumed, ReflectionUtil.getField(AWTEvent.class, event, boolean.class, "consumed").booleanValue());
    assertTrue(ReflectionUtil.getField(AWTEvent.class, event, boolean.class, "isPosted"));
  }
  private static boolean isConsumed(InputEvent event) {
    return event.isConsumed();
  }

  // need this because everybody can post some crazy stuff to IdeEventQueue, so we have to filter InvocationEvents out
  private static AWTEvent dispatchAllInvocationEventsUntilOtherEvent(IdeEventQueue ideEventQueue) throws InterruptedException {
    while (true) {
      AWTEvent event = PlatformTestUtil.dispatchNextEventIfAny(ideEventQueue);
      LOG.debug("event dispatched in dispatchAll() "+event+"; -"+(event instanceof InvocationEvent ? "continuing" : "returning"));
      if (!(event instanceof InvocationEvent)) return event;
    }
  }
}
