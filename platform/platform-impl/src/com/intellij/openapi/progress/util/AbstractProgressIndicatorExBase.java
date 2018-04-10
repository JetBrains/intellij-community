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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class AbstractProgressIndicatorExBase extends AbstractProgressIndicatorBase implements ProgressIndicatorEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.progress.util.ProgressIndicatorBase");
  private final boolean myReusable;
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
      delegateRunningChange(ProgressIndicator::start);
    }
    myWasStarted = true;
  }


  @Override
  public void stop() {
    super.stop();
    delegateRunningChange(ProgressIndicator::stop);
  }

  @Override
  public void cancel() {
    super.cancel();
    delegateRunningChange(ProgressIndicator::cancel);
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

    delegateRunningChange(each -> each.finish(task));
  }

  @Override
  public boolean isFinished(@NotNull final TaskInfo task) {
    Collection<TaskInfo> list = myFinished;
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

    delegate(ProgressIndicator::checkCanceled);
  }

  @Override
  public void setText(final String text) {
    super.setText(text);

    delegateProgressChange(each -> each.setText(text));
  }

  @Override
  public void setText2(final String text) {
    super.setText2(text);

    delegateProgressChange(each -> each.setText2(text));
  }

  @Override
  public void setFraction(final double fraction) {
    super.setFraction(fraction);

    delegateProgressChange(each -> each.setFraction(fraction));
  }

  @Override
  public synchronized void pushState() {
    super.pushState();

    delegateProgressChange(ProgressIndicator::pushState);
  }

  @Override
  public synchronized void popState() {
    super.popState();

    delegateProgressChange(ProgressIndicator::popState);
  }

  @Override
  public void startNonCancelableSection() {
    super.startNonCancelableSection();

    delegateProgressChange(ProgressIndicator::startNonCancelableSection);
  }

  @Override
  public void finishNonCancelableSection() {
    super.finishNonCancelableSection();

    delegateProgressChange(ProgressIndicator::finishNonCancelableSection);
  }

  @Override
  protected boolean isReuseable() {
    return myReusable;
  }

  @Override
  public void setIndeterminate(final boolean indeterminate) {
    super.setIndeterminate(indeterminate);

    delegateProgressChange(each -> each.setIndeterminate(indeterminate));
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
  public boolean wasStarted() {
    return myWasStarted;
  }

  @FunctionalInterface
  protected interface IndicatorAction {
    void execute(@NotNull ProgressIndicatorEx each);
  }
}
