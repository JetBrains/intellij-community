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
package com.intellij.openapi.progress.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.application.impl.ModalityStateEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.DoubleArrayList;
import com.intellij.util.containers.Stack;
import com.intellij.util.containers.WeakList;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CopyOnWriteArrayList;

public class ProgressIndicatorBase extends UserDataHolderBase implements ProgressIndicatorEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.progress.util.ProgressIndicatorBase");

  private volatile String myText;
  private volatile double myFraction;
  private volatile String myText2;

  private volatile boolean myCanceled;
  private volatile boolean myRunning;

  private volatile boolean myIndeterminate;

  private Stack<String> myTextStack;
  private DoubleArrayList myFractionStack; 
  private Stack<String> myText2Stack;
  private volatile int myNonCancelableCount;

  private ProgressIndicator myModalityProgress;
  private ModalityState myModalityState = ModalityState.NON_MODAL;
  private volatile boolean myModalityEntered;

  private volatile CopyOnWriteArrayList<ProgressIndicatorEx> myStateDelegates;
  private volatile WeakList<TaskInfo> myFinished;
  private volatile boolean myWasStarted;

  private TaskInfo myOwnerTask;
  private static final IndicatorAction CHECK_CANCELED_ACTION = new IndicatorAction() {
    public void execute(final ProgressIndicatorEx each) {
      each.checkCanceled();
    }
  };
  private static final IndicatorAction STOP_ACTION = new IndicatorAction() {
    public void execute(final ProgressIndicatorEx each) {
      each.stop();
    }
  };
  private static final IndicatorAction START_ACTION = new IndicatorAction() {
    public void execute(final ProgressIndicatorEx each) {
      each.start();
    }
  };
  private static final IndicatorAction CANCEL_ACTION = new IndicatorAction() {
    public void execute(final ProgressIndicatorEx each) {
      each.cancel();
    }
  };
  private static final IndicatorAction PUSH_ACTION = new IndicatorAction() {
    public void execute(final ProgressIndicatorEx each) {
      each.pushState();
    }
  };
  private static final IndicatorAction POP_ACTION = new IndicatorAction() {
    public void execute(final ProgressIndicatorEx each) {
      each.popState();
    }
  };
  private static final IndicatorAction STARTNC_ACTION = new IndicatorAction() {
    public void execute(final ProgressIndicatorEx each) {
      each.startNonCancelableSection();
    }
  };
  private static final IndicatorAction FINISHNC_ACTION = new IndicatorAction() {
    public void execute(final ProgressIndicatorEx each) {
      each.finishNonCancelableSection();
    }
  };

  public void start() {
    synchronized (this) {
      LOG.assertTrue(!isRunning(), "Attempt to start ProgressIndicator which is already running");
      myText = "";
      myFraction = 0;
      myText2 = "";
      myCanceled = false;
      myRunning = true;
      myWasStarted = true;

      delegateRunningChange(START_ACTION);
    }

    enterModality();
  }

  protected final void enterModality() {
    if (myModalityProgress == this) {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        public void run() {
          doEnterModality();
        }
      });
    }
  }

  private void doEnterModality() {
    if (!myModalityEntered) {
      LaterInvocator.enterModal(this);
      myModalityEntered = true;
    }
  }

  public void stop() {
    LOG.assertTrue(myRunning, "stop() should be called only if start() called before");
    myRunning = false;

    delegateRunningChange(STOP_ACTION);
    exitModality();
  }

  protected final void exitModality() {
    if (myModalityProgress == this) {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        public void run() {
          doExitModality();
         }
      });
    }
  }

  private void doExitModality() {
    if (myModalityEntered) {
      LaterInvocator.leaveModal(this);
      myModalityEntered = false;
    }
  }

  public boolean isRunning() {
    return myRunning;
  }

  public void cancel() {
    myCanceled = true;

    ProgressManagerImpl.canceled();

    delegateRunningChange(CANCEL_ACTION);
  }

  public void finish(@NotNull final TaskInfo task) {
    WeakList<TaskInfo> finished = myFinished;
    if (finished == null) {
      synchronized (this) {
        finished = myFinished;
        if (finished == null) {
          myFinished = finished = new WeakList<TaskInfo>();
        }
      }
    }
    if (!finished.addIfAbsent(task)) return;

    delegateRunningChange(new IndicatorAction() {
      public void execute(final ProgressIndicatorEx each) {
        each.finish(task);
      }
    });
  }

  public boolean isFinished(@NotNull final TaskInfo task) {
    WeakList<TaskInfo> list = myFinished;
    return list != null && list.contains(task);
  }

  protected void setOwnerTask(TaskInfo owner) {
    myOwnerTask = owner;
  }

  public void processFinish() {
    if (myOwnerTask != null) {
      finish(myOwnerTask);
    }
  }

  public boolean isCanceled() {
    return myCanceled;
  }

  public final void checkCanceled() {
    if (isCanceled() && myNonCancelableCount == 0) {
      throw new ProcessCanceledException();
    }

    delegate(CHECK_CANCELED_ACTION);
  }

  public void setText(final String text) {
    myText = text;

    delegateProgressChange(new IndicatorAction() {
      public void execute(final ProgressIndicatorEx each) {
        each.setText(text);
      }
    });
  }

  public String getText() {
    return myText;
  }

  public void setText2(final String text) {
    myText2 = text;

    delegateProgressChange(new IndicatorAction() {
      public void execute(final ProgressIndicatorEx each) {
        each.setText2(text);
      }
    });
  }

  public String getText2() {
    return myText2;
  }

  public double getFraction() {
    return myFraction;
  }

  public void setFraction(final double fraction) {
    myFraction = fraction;

    delegateProgressChange(new IndicatorAction() {
      public void execute(final ProgressIndicatorEx each) {
        each.setFraction(fraction);
      }
    });
  }

  public synchronized void pushState() {
    if (myTextStack == null) myTextStack = new Stack<String>(2);
    myTextStack.push(myText);
    if (myFractionStack == null) myFractionStack = new DoubleArrayList(2);
    myFractionStack.add(myFraction);
    if (myText2Stack == null) myText2Stack = new Stack<String>(2);
    myText2Stack.push(myText2);

    delegateProgressChange(PUSH_ACTION);
  }

  public synchronized void popState() {
    LOG.assertTrue(!myTextStack.isEmpty());
    String oldText = myTextStack.pop();
    double oldFraction = myFractionStack.remove(myFractionStack.size() - 1);
    String oldText2 = myText2Stack.pop();
    setText(oldText);
    setFraction(oldFraction);
    setText2(oldText2);

    delegateProgressChange(POP_ACTION);
  }

  public void startNonCancelableSection() {
    myNonCancelableCount++;

    delegateProgressChange(STARTNC_ACTION);
  }

  public void finishNonCancelableSection() {
    myNonCancelableCount--;

    delegateProgressChange(FINISHNC_ACTION);
  }

  protected boolean isCancelable() {
    return myNonCancelableCount == 0;
  }

  public final boolean isModal() {
    return myModalityProgress != null;
  }

  public final ModalityState getModalityState() {
    return myModalityState;
  }

  public void setModalityProgress(ProgressIndicator modalityProgress) {
    LOG.assertTrue(!isRunning());
    myModalityProgress = modalityProgress;
    ModalityState currentModality = ApplicationManager.getApplication().getCurrentModalityState();
    myModalityState = myModalityProgress != null ? ((ModalityStateEx)currentModality).appendProgress(myModalityProgress) : currentModality;
  }

  public boolean isIndeterminate() {
    return myIndeterminate;
  }

  public void setIndeterminate(final boolean indeterminate) {
    myIndeterminate = indeterminate;


    delegateProgressChange(new IndicatorAction() {
      public void execute(final ProgressIndicatorEx each) {
        each.setIndeterminate(indeterminate);
      }
    });
  }

  public final void addStateDelegate(@NotNull ProgressIndicatorEx delegate) {
    delegate.initStateFrom(this);
    CopyOnWriteArrayList<ProgressIndicatorEx> stateDelegates = myStateDelegates;
    if (stateDelegates == null) {
      synchronized (this) {
        stateDelegates = myStateDelegates;
        if (stateDelegates == null) {
          myStateDelegates = stateDelegates = ContainerUtil.createEmptyCOWList();
        }
      }
    }
    stateDelegates.addIfAbsent(delegate);
  }

  private void delegateProgressChange(IndicatorAction action) {
    delegate(action);
    onProgressChange();
  }

  private void delegateRunningChange(IndicatorAction action) {
    delegate(action);
    onRunningChange();
  }

  private void delegate(IndicatorAction action) {
    CopyOnWriteArrayList<ProgressIndicatorEx> list = myStateDelegates;
    if (list != null) {
      for (ProgressIndicatorEx each : list) {
        action.execute(each);
      }
    }
  }

  private interface IndicatorAction {
    void execute(ProgressIndicatorEx each);
  }


  protected void onProgressChange() {

  }

  protected void onRunningChange() {

  }

  @NotNull
  public Stack<String> getTextStack() {
    if (myTextStack == null) myTextStack = new Stack<String>(2);
    return myTextStack;
  }

  @NotNull
  public DoubleArrayList getFractionStack() {
    if (myFractionStack == null) myFractionStack = new DoubleArrayList(2);
    return myFractionStack;
  }

  @NotNull
  public Stack<String> getText2Stack() {
    if (myText2Stack == null) myText2Stack = new Stack<String>(2);
    return myText2Stack;
  }

  public int getNonCancelableCount() {
    return myNonCancelableCount;
  }


  public boolean isModalityEntered() {
    return myModalityEntered;
  }

  public boolean wasStarted() {
    return myWasStarted;
  }

  public synchronized void initStateFrom(@NotNull final ProgressIndicatorEx indicator) {
    myRunning = indicator.isRunning();
    myCanceled = indicator.isCanceled();
    myModalityEntered = indicator.isModalityEntered();
    myFraction = indicator.getFraction();
    myIndeterminate = indicator.isIndeterminate();
    myNonCancelableCount = indicator.getNonCancelableCount();

    myTextStack = new Stack<String>(indicator.getTextStack());
    myText = indicator.getText();

    myText2Stack = new Stack<String>(indicator.getText2Stack());
    myText2 = indicator.getText2();

    myFractionStack = new DoubleArrayList(indicator.getFractionStack());
    myFraction = indicator.getFraction();
  }
}
