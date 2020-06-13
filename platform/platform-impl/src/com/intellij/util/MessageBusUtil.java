// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;

public final class MessageBusUtil {
  private static <T> Runnable createPublisherRunnable(final Project project, final Topic<? extends T> topic, final Consumer<? super T> listener) {
    return () -> {
      if (project.isDisposed()) throw new ProcessCanceledException();
      listener.consume(project.getMessageBus().syncPublisher(topic));
    };
  }

  public static <T> void invokeLaterIfNeededOnSyncPublisher(final Project project, final Topic<? extends T> topic, final Consumer<? super T> listener) {
    final Application application = ApplicationManager.getApplication();
    final Runnable runnable = createPublisherRunnable(project, topic, listener);
    if (application.isDispatchThread()) {
      runnable.run();
    } else {
      application.invokeLater(runnable);
    }
  }
}
