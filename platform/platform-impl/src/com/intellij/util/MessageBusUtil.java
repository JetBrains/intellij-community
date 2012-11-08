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
package com.intellij.util;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 11/8/12
 * Time: 1:29 PM
 */
public class MessageBusUtil {
  public static <T> void runOnSyncPublisher(final Project project, final Topic<T> topic, final Consumer<T> listener) {
    final Application application = ApplicationManager.getApplication();
    final Runnable runnable = createPublisherRunnable(project, topic, listener);
    if (application.isDispatchThread()) {
      runnable.run();
    } else {
      application.runReadAction(runnable);
    }
  }

  private static <T> Runnable createPublisherRunnable(final Project project, final Topic<T> topic, final Consumer<T> listener) {
    return new Runnable() {
      @Override
      public void run() {
        if (project.isDisposed()) throw new ProcessCanceledException();
        listener.consume(project.getMessageBus().syncPublisher(topic));
      }
    };
  }

  public static <T> void invokeLaterIfNeededOnSyncPublisher(final Project project, final Topic<T> topic, final Consumer<T> listener) {
    final Application application = ApplicationManager.getApplication();
    final Runnable runnable = createPublisherRunnable(project, topic, listener);
    if (application.isDispatchThread()) {
      runnable.run();
    } else {
      application.invokeLater(runnable);
    }
  }
}
