/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.application.*;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.ui.GuiUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AbstractProgressIndicatorExBase extends AbstractProgressIndicatorBase implements ProgressIndicatorEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.progress.util.ProgressIndicatorBase");
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
  protected final boolean myReusable;
  private volatile boolean myModalityEntered;
  private volatile List<ProgressIndicatorEx> myStateDelegates;
  private volatile WeakList<TaskInfo> myFinished;
  private volatile boolean myWasStarted;
  private TaskInfo myOwnerTask;

  public AbstractProgressIndicatorExBase(boolean reusable) {
    myReusable = reusable;
  }

  public AbstractProgressIndicatorExBase() {
    this(false);
  }

  @Override
  public void start() {
    synchronized (this) {
      super.start();
      delegateRunningChange(START_ACTION);
    }
    myWasStarted = true;

    enterModality();
  }

  protected final void enterModality() {
    if (myModalityProgress == this) {
      ModalityState modalityState = ModalityState.defaultModalityState();
      if (!myModalityEntered &&
          !ApplicationManager.getApplication().isDispatchThread() &&
          !((TransactionGuardImpl)TransactionGuard.getInstance()).isWriteSafeModality(modalityState)) {
        // exceptions here should be assigned to Peter
        LOG.error("Non-modal progress should be started in a write-safe context: an action or modality-aware invokeLater. See also TransactionGuard documentation.");
      }
      GuiUtils.invokeLaterIfNeeded(this::doEnterModality, modalityState);
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
    super.stop();
    delegateRunningChange(STOP_ACTION);
    exitModality();
  }

  protected final void exitModality() {
    if (myModalityProgress == this) {
      GuiUtils.invokeLaterIfNeeded(this::doExitModality, ModalityState.defaultModalityState());
    }
  }

  private void doExitModality() {
    if (myModalityEntered) {
      myModalityEntered = false;
      LaterInvocator.leaveModal(this);
    }
  }

  @Override
  public void cancel() {
    super.cancel();
    delegateRunningChange(CANCEL_ACTION);
  }

  @Override
  public boolean isCanceled() {
    return super.isCanceled();
  }

  @Override
  public void finish(@NotNull final TaskInfo task) {
    WeakList<TaskInfo> finished = myFinished;
    if (finished == null) {
      synchronized (this) {
        finished = myFinished;
        if (finished == null) {
          myFinished = finished = new WeakList<>();
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
    List<TaskInfo> list = myFinished;
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
  public final void checkCanceled() {
    super.checkCanceled();

    delegate(CHECK_CANCELED_ACTION);
  }

  @Override
  public void setText(final String text) {
    super.setText(text);

    delegateProgressChange(new IndicatorAction() {
      @Override
      public void execute(@NotNull final ProgressIndicatorEx each) {
        each.setText(text);
      }
    });
  }

  @Override
  public void setText2(final String text) {
    super.setText2(text);

    delegateProgressChange(new IndicatorAction() {
      @Override
      public void execute(@NotNull final ProgressIndicatorEx each) {
        each.setText2(text);
      }
    });
  }

  @Override
  public void setFraction(final double fraction) {
    super.setFraction(fraction);

    delegateProgressChange(new IndicatorAction() {
      @Override
      public void execute(@NotNull final ProgressIndicatorEx each) {
        each.setFraction(fraction);
      }
    });
  }

  @Override
  public synchronized void pushState() {
    super.pushState();

    delegateProgressChange(PUSH_ACTION);
  }

  @Override
  public synchronized void popState() {
    super.popState();

    delegateProgressChange(POP_ACTION);
  }

  @Override
  public void startNonCancelableSection() {
    super.startNonCancelableSection();

    delegateProgressChange(STARTNC_ACTION);
  }

  @Override
  public void finishNonCancelableSection() {
    super.finishNonCancelableSection();

    delegateProgressChange(FINISHNC_ACTION);
  }

  @Override
  protected boolean isReuseable() {
    return myReusable;
  }

  @Override
  public void setIndeterminate(final boolean indeterminate) {
    super.setIndeterminate(indeterminate);

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

  protected void delegateProgressChange(@NotNull IndicatorAction action) {
    delegate(action);
    onProgressChange();
  }

  protected void delegateRunningChange(@NotNull IndicatorAction action) {
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

  protected void onProgressChange() {

  }

  protected void onRunningChange() {

  }

  @Override
  public boolean isModalityEntered() {
    return myModalityEntered;
  }

  @Override
  public synchronized void initStateFrom(@NotNull final ProgressIndicator indicator) {
    super.initStateFrom(indicator);
    if (indicator instanceof ProgressIndicatorEx) {
      myModalityEntered = ((ProgressIndicatorEx)indicator).isModalityEntered();
    }
  }

  @Override
  public boolean wasStarted() {
    return myWasStarted;
  }

  protected interface IndicatorAction {
    void execute(@NotNull ProgressIndicatorEx each);
  }
}
