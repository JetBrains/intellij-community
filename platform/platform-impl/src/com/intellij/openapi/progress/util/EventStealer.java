// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ThreadingSupport;
import com.intellij.openapi.application.impl.InternalThreading;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import sun.awt.SunToolkit;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.InvocationEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@ApiStatus.Internal
public class EventStealer {
  private final LinkedBlockingQueue<InputEvent> myInputEvents = new LinkedBlockingQueue<>();
  private final LinkedBlockingQueue<InvocationEvent> myInvocationEvents = new LinkedBlockingQueue<>();
  // The ping funcitonality is needed for cases when EDT simply waits for some process that might occasionally send events to EventStealer.
  // The EDT needs to react to these events ASAP, but not too eagerly, as we'd like to avoid spinning on EDT
  // So the EDT can simply block on the ping queue with the necessary timeout. It will be woken up as soon as any interesting event appears in the event queue.
  private final LinkedBlockingQueue<Object> myPingQueue;
  private final @NotNull Consumer<? super InputEvent> myInputEventDispatcher;

  private static final Object PING = new Object();
  private static final Logger LOG = Logger.getInstance(EventStealer.class);

  EventStealer(@NotNull Disposable parent, @NotNull Consumer<? super InputEvent> inputConsumer) {
    this(parent, false, inputConsumer);
  }

  EventStealer(@NotNull Disposable parent, boolean installPingingQueue, @NotNull Consumer<? super InputEvent> inputConsumer) {
    myInputEventDispatcher = inputConsumer;
    IdeEventQueue.getInstance().addPostEventListener(event -> {
      if (event instanceof MouseEvent me) {
        myInputEvents.offer(me);
        ping();
        return true;
      }
      else if (event instanceof KeyEvent ke && event.getID() != KeyEvent.KEY_TYPED) {
        myInputEvents.offer(ke);
        ping();
        return true;
      }
      if (event instanceof InvocationEvent ie && isUrgentInvocationEvent(event)) {
        myInvocationEvents.offer(ie);
        ping();
        return true;
      }
      return false;
    }, parent);
    if (installPingingQueue) {
      myPingQueue = new LinkedBlockingQueue<>(1);
    } else {
      myPingQueue = null;
    }
  }

  private void ping() {
    if (myPingQueue != null) {
      myPingQueue.offer(PING);
    }
  }


  public static boolean isUrgentInvocationEvent(AWTEvent event) {
    // LWCToolkit does 'invokeAndWait', which blocks native event processing until finished. The OS considers that blockage to be
    // app freeze, stops rendering UI and shows beach-ball cursor. We want the UI to act (almost) normally in write-action progresses,
    // so we let these specific events to be dispatched, hoping they wouldn't access project/code model.

    // problem (IDEA-192282): LWCToolkit event might be posted before PotemkinProgress appears,
    // and it then just sits in the queue blocking the whole UI until the progress is finished.
    String eventString = event.toString();
    // see IDEA-291469 Menu on macOS is invoked inside checkCanceled (PotemkinProgress)
    if (eventString.contains(",runnable=com.intellij.openapi.actionSystem.impl.ActionMenu$$Lambda") ||
        eventString.contains(",runnable=com.intellij.platform.ide.menu.MacNativeActionMenuKt$$Lambda")) {
      return false;
    }
    return event instanceof InternalThreading.TransferredWriteActionEvent ||
           eventString.contains(",runnable=sun.lwawt.macosx.LWCToolkit") ||// [tav] todo: remove in 2022.2
           eventString.contains(",runnable=" + ThreadingSupport.RunnableWithTransferredWriteAction.NAME) ||
           eventString.contains(",runnable=DispatchTerminationEvent") ||
           event.getClass().getName().equals("sun.awt.AWTThreading$TrackedInvocationEvent"); // see JBR-4208
  }


  List<InputEvent> drainUndispatchedInputEvents() {
    List<InputEvent> result = new ArrayList<>();
    myInputEvents.drainTo(result);
    return result;
  }

  void dispatchEvents(int timeoutMs) {
    SunToolkit.flushPendingEvents();
    try {
      while (true) {
        dispatchAllExistingEvents();

        InputEvent event = myInputEvents.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (event == null) return;

        myInputEventDispatcher.accept(event);
      }
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("SameParameterValue")
  void waitForPing(int timeoutMs) {
    if (myPingQueue == null) {
      LOG.error("Ping queue must be installed");
      return;
    }
    try {
      myPingQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }
    catch (InterruptedException e) {
      // simply resume
    }
  }

  String dumpDebugInfo() {
    StringBuilder sb = new StringBuilder();
    sb.append("Input events: ");
    sb.append(myInputEvents.size());
    sb.append(" (");
    for (InputEvent event: myInputEvents) {
      sb.append(event.toString());
      sb.append(", ");
    }
    sb.append("); ");
    sb.append("Invocation events:");
    sb.append(myInvocationEvents.size());
    sb.append("(");
    for (InvocationEvent event: myInvocationEvents) {
      sb.append(event.toString());
      sb.append(", ");
    }
    sb.append(")");
    return sb.toString();
  }

  void dispatchAllExistingEvents() {
    while (true) {
      InvocationEvent event = myInvocationEvents.poll();
      if (event == null) return;

      event.dispatch();
    }
  }
}
