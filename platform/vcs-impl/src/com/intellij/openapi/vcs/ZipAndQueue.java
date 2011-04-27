package com.intellij.openapi.vcs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.SomeQueue;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.changes.BackgroundFromStartOption;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

/**
 * @author irengrig
 *         Date: 4/27/11
 *         Time: 10:38 AM
 */
@SomeQueue
public class ZipAndQueue {
  private final ZipperUpdater myZipperUpdater;
  private final BackgroundTaskQueue myQueue;
  private Runnable myInZipper;
  private Task.Backgroundable myInvokedOnQueue;

  public ZipAndQueue(final Project project, final int interval, final String title, final Runnable runnable) {
    final int correctedInterval = interval <= 0 ? 300 : interval;
    myZipperUpdater = new ZipperUpdater(correctedInterval, Alarm.ThreadToUse.SHARED_THREAD, project);
    myQueue = new BackgroundTaskQueue(project, title);
    myInZipper = new Runnable() {
      @Override
      public void run() {
        myQueue.run(myInvokedOnQueue);
      }
    };
    myInvokedOnQueue = new Task.Backgroundable(project, title, false, BackgroundFromStartOption.getInstance()) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        runnable.run();
      }
    };
    Disposer.register(project, new Disposable() {
      @Override
      public void dispose() {
        myZipperUpdater.stop();
      }
    });
  }

  public void request() {
    myZipperUpdater.queue(myInZipper);
  }

  public void stop() {
    myZipperUpdater.stop();
  }
}
