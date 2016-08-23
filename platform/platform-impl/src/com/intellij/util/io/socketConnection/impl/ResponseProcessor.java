package com.intellij.util.io.socketConnection.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.util.Ref;
import com.intellij.util.Alarm;
import com.intellij.util.SmartList;
import com.intellij.util.io.socketConnection.*;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;
import gnu.trove.TObjectProcedure;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public class ResponseProcessor<R extends AbstractResponse> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.socketConnection.impl.ResponseProcessor");
  private final TIntObjectHashMap<AbstractResponseToRequestHandler<?>> myHandlers = new TIntObjectHashMap<>();
  private final MultiValuesMap<Class<? extends R>, AbstractResponseHandler<? extends R>> myClassHandlers = new MultiValuesMap<>();
  private final TIntObjectHashMap<TimeoutHandler> myTimeoutHandlers = new TIntObjectHashMap<>();
  private boolean myStopped;
  private final Object myLock = new Object();
  private Thread myThread;
  private final Alarm myTimeoutAlarm;

  public ResponseProcessor(SocketConnection<?, R> connection) {
    myTimeoutAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD, connection);
  }

  public void startReading(final ResponseReader<R> reader) {
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


  private void processResponse(R response) throws IOException {
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

  public <T extends R> void registerHandler(@NotNull Class<T> responseClass, @NotNull AbstractResponseHandler<T> handler) {
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
      final long time = System.currentTimeMillis();
      myTimeoutHandlers.retainEntries(new TIntObjectProcedure<TimeoutHandler>() {
        public boolean execute(int a, TimeoutHandler b) {
          if (time > b.myLastTime) {
            timedOut.add(b);
            return false;
          }
          return true;
        }
      });
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
      if (myTimeoutHandlers.isEmpty()) return;
      myTimeoutHandlers.forEachValue(new TObjectProcedure<TimeoutHandler>() {
        public boolean execute(TimeoutHandler handler) {
          nextTime.set(Math.min(nextTime.get(), handler.myLastTime));
          return true;
        }
      });
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

  private static class TimeoutHandler {
    private final Runnable myAction;
    private final long myLastTime;

    private TimeoutHandler(Runnable action, long lastTime) {
      myAction = action;
      myLastTime = lastTime;
    }
  }
}
