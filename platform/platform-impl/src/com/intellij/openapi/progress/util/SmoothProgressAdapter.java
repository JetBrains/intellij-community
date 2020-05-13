/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.StandardProgressIndicator;
import com.intellij.openapi.progress.WrappedProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class SmoothProgressAdapter extends AbstractProgressIndicatorExBase implements ProgressIndicatorEx, WrappedProgressIndicator,
                                                                                      StandardProgressIndicator {
  private static final int SHOW_DELAY = 500;

  private Future<?> myStartupAlarm = CompletableFuture.completedFuture(null);

  private final ProgressIndicator myOriginal;
  private final Project myProject;

  private volatile boolean myOriginalStarted;

  private DialogWrapper myDialog;

  private final Runnable myShowRequest = new Runnable() {
    @Override
    public void run() {
      synchronized(getLock()){
        if (!isRunning()) {
          return;
        }

        myOriginal.start();
        myOriginalStarted = true;

        myOriginal.setText(getText());
        myOriginal.setFraction(getFraction());
        myOriginal.setText2(getText2());
      }
    }
  };

  public SmoothProgressAdapter(@NotNull ProgressIndicator original, @NotNull Project project){
    myOriginal = original;
    myProject = project;
    if (myOriginal.isModal()) {
      myOriginal.setModalityProgress(this);
      setModalityProgress(this);
    }
    ProgressManager.assertNotCircular(original);
    if (original.isRunning() || original.isCanceled()) {
      throw new IllegalArgumentException("Original indicator must be not started and not cancelled: "+original);
    }
  }

  @NotNull
  @Override
  public ProgressIndicator getOriginalProgressIndicator() {
    return myOriginal;
  }

  @Override
  public void setIndeterminate(boolean indeterminate) {
    super.setIndeterminate(indeterminate);
    myOriginal.setIndeterminate(indeterminate);
  }

  @Override
  public boolean isIndeterminate() {
    return myOriginal.isIndeterminate();
  }

  @Override
  public void start() {
    synchronized (getLock()) {
      if (isRunning()) return;

      super.start();
      myOriginalStarted = false;
      myStartupAlarm = AppExecutorUtil.getAppScheduledExecutorService().schedule(myShowRequest, SHOW_DELAY, TimeUnit.MILLISECONDS);
    }
  }

  public void startBlocking() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    start();
    if (isModal()) {
      showDialog();
    }
  }

  private void showDialog(){
    if (myDialog == null){
      //System.out.println("showDialog()");
      myDialog = new DialogWrapper(myProject, false) {
        {
          getWindow().setBounds(0, 0, 1, 1);
          setResizable(false);
        }

        @Override
        protected boolean isProgressDialog() {
          return true;
        }

        @Override
        protected JComponent createCenterPanel() {
          return null;
        }
      };
      myDialog.setModal(true);
      myDialog.setUndecorated(true);
      myDialog.show();
    }
  }

  @Override
  public void stop() {
    synchronized (getLock()) {
      if (myOriginal.isRunning()) {
        myOriginalStarted = true;
        myOriginal.stop();
      }
      myStartupAlarm.cancel(false);

      // needed only for correct assertion of !progress.isRunning() in ApplicationImpl.runProcessWithProgressSynchronously
      final Semaphore semaphore = new Semaphore();
      semaphore.down();

      SwingUtilities.invokeLater(
        () -> {
          if (!myOriginalStarted && myOriginal instanceof Disposable) {
            // dispose original because start & stop were not called so original progress might not have released its resources
            Disposer.dispose((Disposable)myOriginal);
          }

          semaphore.waitFor();
          if (myDialog != null){
            myDialog.close(DialogWrapper.OK_EXIT_CODE);
            myDialog = null;
          }
        }
      );

      try {
        super.stop(); // should be last to not leaveModal before closing the dialog
      }
      finally {
        semaphore.up();
      }
    }
  }

  @Override
  public void setText(String text) {
    synchronized (getLock()) {
      super.setText(text);
      if (myOriginal.isRunning()) {
        myOriginal.setText(text);
      }
    }
  }

  @Override
  public void setFraction(double fraction) {
    synchronized (getLock()) {
      super.setFraction(fraction);
      if (myOriginal.isRunning()) {
        myOriginal.setFraction(fraction);
      }
    }
  }
  
  @Override
  public void setText2(String text) {
    synchronized (getLock()) {
      super.setText2(text);
      if (myOriginal.isRunning()) {
        myOriginal.setText2(text);
      }
    }
  }

  @Override
  public final void cancel() {
    super.cancel();
    myOriginal.cancel();
  }

  @Override
  public final boolean isCanceled() {
    return super.isCanceled() || myOriginalStarted && myOriginal.isCanceled();
  }
}
