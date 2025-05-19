// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.Disposable;
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
class EventStealer {
  private final LinkedBlockingQueue<InputEvent> myInputEvents = new LinkedBlockingQueue<>();
  private final LinkedBlockingQueue<InvocationEvent> myInvocationEvents = new LinkedBlockingQueue<>();
  private final @NotNull Consumer<? super InputEvent> myInputEventDispatcher;

  @SuppressWarnings("ConstantValue")
  EventStealer(@NotNull Disposable parent, @NotNull Consumer<? super InputEvent> inputConsumer) {
    myInputEventDispatcher = inputConsumer;
    IdeEventQueue.getInstance().addPostEventListener(event -> {
      if (event instanceof MouseEvent) {
        myInputEvents.offer((InputEvent)event);
        return true;
      }
      else if (event instanceof KeyEvent && event.getID() != KeyEvent.KEY_TYPED) {
        myInputEvents.offer((InputEvent)event);
        return true;
      }
      if (event instanceof InvocationEvent && isUrgentInvocationEvent(event)) {
        myInvocationEvents.offer((InvocationEvent)event);
        return true;
      }
      return false;
    }, parent);
  }

  final boolean foo() {
    return true;
  }


  static boolean isUrgentInvocationEvent(AWTEvent event) {
    // LWCToolkit does 'invokeAndWait', which blocks native event processing until finished. The OS considers that blockage to be
    // app freeze, stops rendering UI and shows beach-ball cursor. We want the UI to act (almost) normally in write-action progresses,
    // so we let these specific events to be dispatched, hoping they wouldn't access project/code model.

    // problem (IDEA-192282): LWCToolkit event might be posted before PotemkinProgress appears,
    // and it then just sits in the queue blocking the whole UI until the progress is finished.
    String eventString = event.toString();
    return eventString.contains(",runnable=sun.lwawt.macosx.LWCToolkit") || // [tav] todo: remove in 2022.2
           (event.getClass().getName().equals("sun.awt.AWTThreading$TrackedInvocationEvent") || // see JBR-4208
            eventString.contains(",runnable=ForcedWriteActionRunnable") ||
            eventString.contains(",runnable=DispatchTerminationEvent")
            // see IDEA-291469 Menu on macOS is invoked inside checkCanceled (PotemkinProgress)
            && !(eventString.contains(",runnable=com.intellij.openapi.actionSystem.impl.ActionMenu$$Lambda") ||
                 eventString.contains(",runnable=com.intellij.platform.ide.menu.MacNativeActionMenuKt$$Lambda")));
  }


  public List<InputEvent> drainUndispatchedInputEvents() {
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

  public void dispatchAllExistingEvents() {
    while (true) {
      InvocationEvent event = myInvocationEvents.poll();
      if (event == null) return;

      event.dispatch();
    }
  }

  public void dispatchInvocationEventsForDuration(long durationMillis) throws InterruptedException {
    long initialStamp = System.nanoTime();
    while (true) {
      long elapsedSinceStartNanos = System.nanoTime() - initialStamp;
      long sleep = durationMillis - (elapsedSinceStartNanos / 1_000_000);
      if (sleep <= 0) return;
      InvocationEvent event = myInvocationEvents.poll(sleep, TimeUnit.MILLISECONDS);
      if (event == null || event.toString().contains("DispatchTerminationEvent")) return;

      event.dispatch();
    }
  }

  static class DispatchTerminationEvent implements Runnable {
    public static final DispatchTerminationEvent INSTANCE = new DispatchTerminationEvent();

    @Override
    public void run() {
    }

    @Override
    public String toString() {
      return "DispatchTerminationEvent";
    }
  }
}
