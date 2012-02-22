/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.android.designer.designSurface;

import com.intellij.openapi.application.ModalityState;
import com.intellij.psi.*;
import com.intellij.util.Alarm;

import javax.swing.*;

/**
 * @author Alexander Lobas
 */
public class ExternalPSIChangeListener extends PsiTreeChangeAdapter {
  private final Alarm myAlarm = new Alarm();
  private final JComponent myComponent;
  private final PsiFile myFile;
  private final int myDelayMillis;
  private final Runnable myRunnable;
  private volatile boolean myRunState;

  public ExternalPSIChangeListener(JComponent component, PsiFile file, int delayMillis, Runnable runnable) {
    myComponent = component;
    myFile = file;
    myDelayMillis = delayMillis;
    myRunnable = runnable;
  }

  public void start() {
    if (!myRunState) {
      myRunState = true;
      PsiManager.getInstance(myFile.getProject()).addPsiTreeChangeListener(this);
    }
  }

  public void stop() {
    if (myRunState) {
      myRunState = false;
      PsiManager.getInstance(myFile.getProject()).removePsiTreeChangeListener(this);
      myAlarm.cancelAllRequests();
    }
  }

  private void update(PsiTreeChangeEvent event) {
    if (myRunState && myFile == event.getFile()) {
      myAlarm.cancelAllRequests();
      myAlarm.addRequest(new Runnable() {
        @Override
        public void run() {
          if (myRunState) {
            myRunnable.run();
          }
        }
      }, myDelayMillis, ModalityState.stateForComponent(myComponent));
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // PSI
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public void childAdded(PsiTreeChangeEvent event) {
    update(event);
  }

  @Override
  public void childRemoved(PsiTreeChangeEvent event) {
    update(event);
  }

  @Override
  public void childReplaced(PsiTreeChangeEvent event) {
    update(event);
  }

  @Override
  public void childMoved(PsiTreeChangeEvent event) {
    update(event);
  }

  @Override
  public void childrenChanged(PsiTreeChangeEvent event) {
    update(event);
  }

  @Override
  public void propertyChanged(PsiTreeChangeEvent event) {
    update(event);
  }
}