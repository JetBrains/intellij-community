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
package com.intellij.remoteServer.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;

import javax.swing.*;

public abstract class DelayedRunner implements Disposable {

  private static final int CHANGES_CHECK_TIME = 500;
  private static final int RUN_DELAY_TIME = 2000;
  private static final int NO_CHANGES = -1;

  private final Alarm myAlarm;

  private int myChangesPastTime = NO_CHANGES;

  public DelayedRunner(JComponent activationComponent) {
    myAlarm = new Alarm().setActivationComponent(activationComponent);
    queueChangesCheck();
  }

  private void queueChangesCheck() {
    if (myAlarm.isDisposed()) {
      return;
    }
    myAlarm.addRequest(new Runnable() {

      @Override
      public void run() {
        checkChanges();
        queueChangesCheck();
      }
    }, CHANGES_CHECK_TIME, ModalityState.any());
  }

  private void checkChanges() {
    if (wasChanged()) {
      myChangesPastTime = 0;
    }
    else {
      if (myChangesPastTime != NO_CHANGES) {
        myChangesPastTime += CHANGES_CHECK_TIME;
        if (myChangesPastTime >= RUN_DELAY_TIME) {
          myChangesPastTime = NO_CHANGES;

          run();
        }
      }
    }
  }

  @Override
  public void dispose() {
    Disposer.dispose(myAlarm);
  }

  protected abstract boolean wasChanged();

  protected abstract void run();
}
