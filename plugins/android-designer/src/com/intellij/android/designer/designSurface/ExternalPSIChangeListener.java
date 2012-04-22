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
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ComparatorUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Lobas
 */
public class ExternalPSIChangeListener extends PsiTreeChangeAdapter {
  private final Alarm myAlarm = new Alarm();
  private final AndroidDesignerEditorPanel myDesigner;
  private final PsiFile myFile;
  private final int myDelayMillis;
  private final Runnable myRunnable;
  private volatile boolean myRunState;
  private volatile boolean myInitialize;
  private String myContent;

  public ExternalPSIChangeListener(AndroidDesignerEditorPanel designer, PsiFile file, int delayMillis, Runnable runnable) {
    myDesigner = designer;
    myFile = file;
    myDelayMillis = delayMillis;
    myRunnable = runnable;
    myContent = myDesigner.getEditorText();
  }

  public void setInitialize() {
    myInitialize = true;
  }

  public void start() {
    if (!myRunState) {
      myRunState = true;
      PsiManager.getInstance(myDesigner.getProject()).addPsiTreeChangeListener(this);
    }
  }

  public void stop() {
    if (myRunState) {
      myRunState = false;
      PsiManager.getInstance(myDesigner.getProject()).removePsiTreeChangeListener(this);
      clear();
    }
  }

  public void activate() {
    if (!myRunState) {
      start();
      if (!ComparatorUtil.equalsNullable(myContent, myDesigner.getEditorText()) || myDesigner.getRootComponent() == null) {
        addRequest();
      }
      myContent = null;
    }
  }

  public void deactivate() {
    if (myRunState) {
      stop();
      myContent = myDesigner.getEditorText();
    }
  }

  private void updatePsi(PsiTreeChangeEvent event) {
    if (myRunState && myFile == event.getFile()) {
      addRequest();
    }
  }

  public void addRequest() {
    addRequest(myRunnable);
  }

  public void addRequest(final Runnable runnable) {
    clear();
    myAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        if (myRunState && myInitialize && !myDesigner.getProject().isDisposed()) {
          runnable.run();
        }
      }
    }, myDelayMillis, ModalityState.stateForComponent(myDesigner));
  }

  public void clear() {
    myAlarm.cancelAllRequests();
  }
  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // PSI
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public void childAdded(@NotNull PsiTreeChangeEvent event) {
    updatePsi(event);
  }

  @Override
  public void childRemoved(@NotNull PsiTreeChangeEvent event) {
    updatePsi(event);
  }

  @Override
  public void childReplaced(@NotNull PsiTreeChangeEvent event) {
    updatePsi(event);
  }

  @Override
  public void childMoved(@NotNull PsiTreeChangeEvent event) {
    updatePsi(event);
  }

  @Override
  public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
    updatePsi(event);
  }

  @Override
  public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
    updatePsi(event);
  }
}