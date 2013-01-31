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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
  private volatile ModalityState myModalityState = ModalityState.NON_MODAL;
  private volatile boolean myModalityEntered;

  private volatile List<ProgressIndicatorEx> myStateDelegates;
  private volatile WeakList<TaskInfo> myFinished;
  private volatile boolean myWasStarted;

  private TaskInfo myOwnerTask;
  private static final IndicatorAction CHECK_CANCELED_ACTION = new IndicatorAction() {
    @Override
    public void execute(@NotNull ProgressIndicatorEx each) {
      each.checkCanceled();
    }
  };
  private static final IndicatorAction STOP_ACTION = new IndicatorAction() {
    @Override
    public void execute(@NotNull final ProgressIndicatorEx each) {
      each.stop();
    }
  };
  private static final IndicatorAction START_ACTION = new IndicatorAction() {
    @Override
    public void execute(@NotNull final ProgressIndicatorEx each) {
      each.start();
    }
  };
  private static final IndicatorAction CANCEL_ACTION = new IndicatorAction() {
    @Override
    public void execute(@NotNull final ProgressIndicatorEx each) {
      each.cancel();
    }
  };
  private static final IndicatorAction PUSH_ACTION = new IndicatorAction() {
    @Override
    public void execute(@NotNull final ProgressIndicatorEx each) {
      each.pushState();
    }
  };
  private static final IndicatorAction POP_ACTION = new IndicatorAction() {
    @Override
    public void execute(@NotNull final ProgressIndicatorEx each) {
      each.popState();
    }
  };
  private static final IndicatorAction STARTNC_ACTION = new IndicatorAction() {
    @Override
    public void execute(@NotNull final ProgressIndicatorEx each) {
      each.startNonCancelableSection();
    }
  };
  private static final IndicatorAction FINISHNC_ACTION = new IndicatorAction() {
    @Override
    public void execute(@NotNull final ProgressIndicatorEx each) {
      each.finishNonCancelableSection();
    }
  };

  @Override
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
        @Override
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

  @Override
  public void stop() {
    LOG.assertTrue(myRunning, "stop() should be called only if start() called before");
    myRunning = false;

    delegateRunningChange(STOP_ACTION);
    exitModality();
  }

  protected final void exitModality() {
    if (myModalityProgress == this) {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
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

  @Override
  public boolean isRunning() {
    return myRunning;
  }

  @Override
  public void cancel() {
    myCanceled = true;

    ProgressManagerImpl.canceled();

    delegateRunningChange(CANCEL_ACTION);
  }

  @Override
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
      @Override
      public void execute(@NotNull final ProgressIndicatorEx each) {
        each.finish(task);
      }
    });
  }

  @Override
  public boolean isFinished(@NotNull final TaskInfo task) {
    WeakList<TaskInfo> list = myFinished;
    return list != null && list.contains(task);
  }

  protected void setOwnerTask(TaskInfo owner) {
    myOwnerTask = owner;
  }

  @Override
  public void processFinish() {
    if (myOwnerTask != null) {
      finish(myOwnerTask);
      myOwnerTask = null;
    }
  }

  @Override
  public boolean isCanceled() {
    return myCanceled;
  }

  @Override
  public final void checkCanceled() {
    if (isCanceled() && isCancelable()) {
      throw new ProcessCanceledException();
    }

    delegate(CHECK_CANCELED_ACTION);
  }

  @Override
  public void setText(final String text) {
    myText = text;

    delegateProgressChange(new IndicatorAction() {
      @Override
      public void execute(@NotNull final ProgressIndicatorEx each) {
        each.setText(text);
      }
    });
  }

  @Override
  public String getText() {
    return myText;
  }

  @Override
  public void setText2(final String text) {
    myText2 = text;

    delegateProgressChange(new IndicatorAction() {
      @Override
      public void execute(@NotNull final ProgressIndicatorEx each) {
        each.setText2(text);
      }
    });
  }

  @Override
  public String getText2() {
    return myText2;
  }

  @Override
  public double getFraction() {
    return myFraction;
  }

  @Override
  public void setFraction(final double fraction) {
    myFraction = fraction;

    delegateProgressChange(new IndicatorAction() {
      @Override
      public void execute(@NotNull final ProgressIndicatorEx each) {
        each.setFraction(fraction);
      }
    });
  }

  @Override
  public synchronized void pushState() {
    if (myTextStack == null) myTextStack = new Stack<String>(2);
    myTextStack.push(myText);
    if (myFractionStack == null) myFractionStack = new DoubleArrayList(2);
    myFractionStack.add(myFraction);
    if (myText2Stack == null) myText2Stack = new Stack<String>(2);
    myText2Stack.push(myText2);

    delegateProgressChange(PUSH_ACTION);
  }

  @Override
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

  @Override
  public void startNonCancelableSection() {
    myNonCancelableCount++;

    delegateProgressChange(STARTNC_ACTION);
  }

  @Override
  public void finishNonCancelableSection() {
    myNonCancelableCount--;

    delegateProgressChange(FINISHNC_ACTION);
  }

  protected boolean isCancelable() {
    return myNonCancelableCount == 0;
  }

  @Override
  public final boolean isModal() {
    return myModalityProgress != null;
  }

  @Override
  @NotNull
  public final ModalityState getModalityState() {
    return myModalityState;
  }

  @Override
  public void setModalityProgress(ProgressIndicator modalityProgress) {
    LOG.assertTrue(!isRunning());
    myModalityProgress = modalityProgress;
    ModalityState currentModality = ApplicationManager.getApplication().getCurrentModalityState();
    myModalityState = myModalityProgress != null ? ((ModalityStateEx)currentModality).appendProgress(myModalityProgress) : currentModality;
  }

  @Override
  public boolean isIndeterminate() {
    return myIndeterminate;
  }

  @Override
  public void setIndeterminate(final boolean indeterminate) {
    myIndeterminate = indeterminate;


    delegateProgressChange(new IndicatorAction() {
      @Override
      public void execute(@NotNull final ProgressIndicatorEx each) {
        each.setIndeterminate(indeterminate);
      }
    });
  }

  @Override
  public final void addStateDelegate(@NotNull ProgressIndicatorEx delegate) {
    delegate.initStateFrom(this);
    synchronized (this) {
      List<ProgressIndicatorEx> stateDelegates = myStateDelegates;
      if (stateDelegates == null) {
        myStateDelegates = stateDelegates = ContainerUtil.createLockFreeCopyOnWriteList();
      }
      else {
        LOG.assertTrue(!stateDelegates.contains(delegate), "Already registered: " + delegate);
      }
      stateDelegates.add(delegate);
    }
  }

  private void delegateProgressChange(@NotNull IndicatorAction action) {
    delegate(action);
    onProgressChange();
  }

  private void delegateRunningChange(@NotNull IndicatorAction action) {
    delegate(action);
    onRunningChange();
  }

  private void delegate(@NotNull IndicatorAction action) {
    List<ProgressIndicatorEx> list = myStateDelegates;
    if (list != null && !list.isEmpty()) {
      for (ProgressIndicatorEx each : list) {
        action.execute(each);
      }
    }
  }

  private interface IndicatorAction {
    void execute(@NotNull ProgressIndicatorEx each);
  }


  protected void onProgressChange() {

  }

  protected void onRunningChange() {

  }

  @Override
  @NotNull
  public Stack<String> getTextStack() {
    if (myTextStack == null) myTextStack = new Stack<String>(2);
    return myTextStack;
  }

  @Override
  @NotNull
  public DoubleArrayList getFractionStack() {
    if (myFractionStack == null) myFractionStack = new DoubleArrayList(2);
    return myFractionStack;
  }

  @Override
  @NotNull
  public Stack<String> getText2Stack() {
    if (myText2Stack == null) myText2Stack = new Stack<String>(2);
    return myText2Stack;
  }

  @Override
  public int getNonCancelableCount() {
    return myNonCancelableCount;
  }


  @Override
  public boolean isModalityEntered() {
    return myModalityEntered;
  }

  @Override
  public boolean wasStarted() {
    return myWasStarted;
  }

  @Override
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

  @NonNls
  @Override
  public String toString() {
    return "ProgressIndicator " + System.identityHashCode(this) + ": running="+isRunning()+"; canceled="+isCanceled();
  }

  @Override
  public boolean isPopupWasShown() {
    return true;
  }

  @Override
  public boolean isShowing() {
    return isModal();
  }
}
