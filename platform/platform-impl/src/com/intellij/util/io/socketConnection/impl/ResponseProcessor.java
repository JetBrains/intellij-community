// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.socketConnection.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.util.Ref;
import com.intellij.util.Alarm;
import com.intellij.util.SmartList;
import com.intellij.util.io.socketConnection.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class ResponseProcessor<R extends AbstractResponse> {
  private static final Logger LOG = Logger.getInstance(ResponseProcessor.class);
  private final Int2ObjectMap<AbstractResponseToRequestHandler<?>> myHandlers = new Int2ObjectOpenHashMap<>();
  private final MultiValuesMap<Class<? extends R>, AbstractResponseHandler<? extends R>> myClassHandlers = new MultiValuesMap<>();
  private final Int2ObjectMap<TimeoutHandler> myTimeoutHandlers = new Int2ObjectOpenHashMap<>();
  private boolean myStopped;
  private final Object myLock = new Object();
  private Thread myThread;
  private final Alarm myTimeoutAlarm;

  public ResponseProcessor(@NotNull SocketConnection<?, R> connection) {
    myTimeoutAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, connection);
  }

  public void startReading(final ResponseReader<? extends R> reader) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      myThread = Thread.currentThread();
      try {
        while (true) {
          final R r = reader.readResponse();
          if (r == null) break;
          if (r instanceof ResponseToRequest) {
            final int requestId = ((ResponseToRequest)r).getRequestId();
            processResponse(requestId, r);
          }
          else {
            processResponse(r);
          }
        }
      }
      catch (InterruptedException ignored) {
      }
      catch (IOException e) {
        LOG.info(e);
      }
      finally {
        synchronized (myLock) {
          myStopped = true;
        }
      }
    });
  }

  private void processResponse(int requestId, R response) {
    synchronized (myLock) {
      myTimeoutHandlers.remove(requestId);
    }

    final AbstractResponseToRequestHandler handler;
    synchronized (myLock) {
      handler = myHandlers.remove(requestId);
      if (handler == null) return;
    }

    //noinspection unchecked
    if (!handler.processResponse(response)) {
      synchronized (myLock) {
        myHandlers.put(requestId, handler);
      }
    }
  }


  private void processResponse(R response) {
    //noinspection unchecked
    final Class<R> responseClass = (Class<R>)response.getClass();

    List<AbstractResponseHandler<?>> handlers;
    synchronized (myLock) {
      final Collection<AbstractResponseHandler<? extends R>> responseHandlers = myClassHandlers.get(responseClass);
      if (responseHandlers == null) return;
      handlers = new SmartList<>(responseHandlers);
    }

    for (AbstractResponseHandler handler : handlers) {
      //noinspection unchecked
      handler.processResponse(response);
    }
  }

  public void stopReading() {
    synchronized (myLock) {
      if (myStopped) return;
      myStopped = true;
    }

    if (myThread != null) {
      myThread.interrupt();
    }
  }

  public <T extends R> void registerHandler(@NotNull Class<? extends T> responseClass, @NotNull AbstractResponseHandler<T> handler) {
    synchronized (myLock) {
      myClassHandlers.put(responseClass, handler);
    }
  }

  public void registerHandler(int id, @NotNull AbstractResponseToRequestHandler<?> handler) {
    synchronized (myLock) {
      myHandlers.put(id, handler);
    }
  }

  public void checkTimeout() {
    LOG.debug("Checking timeout");
    final List<TimeoutHandler> timedOut = new ArrayList<>();
    synchronized (myLock) {
      long time = System.currentTimeMillis();
      ObjectIterator<Int2ObjectMap.Entry<TimeoutHandler>> iterator = myTimeoutHandlers.int2ObjectEntrySet().iterator();
      while (iterator.hasNext()) {
        Int2ObjectMap.Entry<TimeoutHandler> entry = iterator.next();
        TimeoutHandler b = entry.getValue();
        if (time > b.myLastTime) {
          timedOut.add(b);
          iterator.remove();
        }
      }
    }
    for (TimeoutHandler handler : timedOut) {
      LOG.debug("performing timeout action: " + handler.myAction);
      handler.myAction.run();
    }
    scheduleTimeoutCheck();
  }

  private void scheduleTimeoutCheck() {
    final Ref<Long> nextTime = Ref.create(Long.MAX_VALUE);
    synchronized (myLock) {
      if (myTimeoutHandlers.isEmpty()) {
        return;
      }

      for (TimeoutHandler value : myTimeoutHandlers.values()) {
        nextTime.set(Math.min(nextTime.get(), value.myLastTime));
      }
    }
    final int delay = (int)(nextTime.get() - System.currentTimeMillis() + 100);
    LOG.debug("schedule timeout check in " + delay + "ms");
    if (delay > 10) {
      myTimeoutAlarm.cancelAllRequests();
      myTimeoutAlarm.addRequest(() -> checkTimeout(), delay);
    }
    else {
      checkTimeout();
    }
  }

  public void registerTimeoutHandler(int commandId, int timeout, Runnable onTimeout) {
    synchronized (myLock) {
      myTimeoutHandlers.put(commandId, new TimeoutHandler(onTimeout, System.currentTimeMillis() + timeout));
    }
    scheduleTimeoutCheck();
  }

  private static final class TimeoutHandler {
    private final Runnable myAction;
    private final long myLastTime;

    private TimeoutHandler(Runnable action, long lastTime) {
      myAction = action;
      myLastTime = lastTime;
    }
  }
}
