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
package com.intellij.openapi.vcs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ThreeState;
import com.intellij.util.ThrowableRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * By the nature of the process, we do not expect here situations where one thread activates/starts and another simultaneosly
 * tries to deactivate/shutdown. It that situations, it would be very hard to decide what should be done=)
 *
 * So, synchronization here should only be used as a barrier to do not allow repeated activation etc.
 * - and "actual" methods should not be called under lock
 */
public abstract class StartedActivated {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.StartedActivated");

  private final MySection myStart;
  private final MySection myActivate;
  private final Object myLock;

  protected StartedActivated(final Disposable parent) {
    myStart = new MySection(new ThrowableRunnable<VcsException>() {
      public void run() throws VcsException {
        start();
      }
    }, new ThrowableRunnable<VcsException>() {
      public void run() throws VcsException {
        shutdown();
      }
    });
    myActivate = new MySection(new ThrowableRunnable<VcsException>() {
      public void run() throws VcsException {
        activate();
      }
    }, new ThrowableRunnable<VcsException>() {
      public void run() throws VcsException {
        deactivate();
      }
    });
    myStart.setDependent(myActivate);
    myActivate.setMaster(myStart);

    myLock = new Object();

    Disposer.register(parent, new Disposable() {
      public void dispose() {
        try {
          doShutdown();
        }
        catch (Throwable t) {
          LOG.info(t);
        }
      }
    });
  }

  // for tests only
  protected StartedActivated() {
    myStart = null;
    myActivate = null;
    myLock = null;
  }

  protected abstract void start() throws VcsException;
  protected abstract void shutdown() throws VcsException;
  protected abstract void activate() throws VcsException;
  protected abstract void deactivate() throws VcsException;

  private void callImpl(final MySection section, final boolean start) throws VcsException {
    final List<ThrowableRunnable<VcsException>> list = new ArrayList<>(2);
    synchronized (myLock) {
      if (start) {
        section.start(list);
      } else {
        section.stop(list);
      }
    }
    for (ThrowableRunnable<VcsException> runnable : list) {
      runnable.run();
    }
  }

  public final void doStart() throws VcsException {
    callImpl(myStart, true);
  }

  public final void doShutdown() throws VcsException {
    callImpl(myStart, false);
  }

  public final void doActivate() throws VcsException {
    callImpl(myActivate, true);
  }
  
  public final void doDeactivate() throws VcsException {
    callImpl(myActivate, false);
  }

  private static class MySection {
    private MySection myMaster;
    private MySection myDependent;

    private final ThrowableRunnable<VcsException> myStart;
    private final ThrowableRunnable<VcsException> myStop;

    private ThreeState myState;

    public MySection(final ThrowableRunnable<VcsException> start, final ThrowableRunnable<VcsException> stop) {
      myStart = start;
      myStop = stop;
      myState = ThreeState.UNSURE;
    }

    public void start(final List<ThrowableRunnable<VcsException>> callList) {
      if (myMaster != null) {
        myMaster.start(callList);
      }
      if (! ThreeState.YES.equals(myState)) {
        myState = ThreeState.YES;
        callList.add(myStart);
      }
    }

    public void stop(final List<ThrowableRunnable<VcsException>> callList) {
      if (myDependent != null) {
        myDependent.stop(callList);
      }
      if (ThreeState.YES.equals(myState)) {
        myState = ThreeState.NO;
        callList.add(myStop);
      }
    }

    public void setMaster(MySection master) {
      myMaster = master;
    }

    public void setDependent(MySection dependent) {
      myDependent = dependent;
    }
  }
}
