/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.service.execution;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import org.gradle.tooling.ProgressEvent;
import org.gradle.tooling.ProgressListener;

/**
 * @author Vladislav.Soroka
 * @since 4/2/2017
 */
public class GradleProgressListener implements ProgressListener, org.gradle.tooling.events.ProgressListener {
  private final ExternalSystemTaskNotificationListener myListener;
  private final ExternalSystemTaskId myTaskId;

  public GradleProgressListener(ExternalSystemTaskNotificationListener listener, ExternalSystemTaskId taskId) {
    myListener = listener;
    myTaskId = taskId;
  }

  @Override
  public void statusChanged(ProgressEvent event) {
    myListener.onStatusChange(new ExternalSystemTaskNotificationEvent(myTaskId, event.getDescription()));
  }

  @Override
  public void statusChanged(org.gradle.tooling.events.ProgressEvent event) {
    myListener.onStatusChange(GradleProgressEventConverter.convert(myTaskId, event));
  }
}
