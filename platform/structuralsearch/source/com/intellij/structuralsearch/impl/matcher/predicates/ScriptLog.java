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
package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.structuralsearch.SSRBundle;

/**
 * @author Bas Leijdekkers
 */
public class ScriptLog {

  private static final NotificationGroup myEventLog = NotificationGroup.logOnlyGroup(SSRBundle.message("structural.search.title"));
  private final Project myProject;

  public ScriptLog(Project project) {
    myProject = project;
  }

  public void info(Object message) {
    log(message, NotificationType.INFORMATION);
  }

  public void warn(Object message) {
    log(message, NotificationType.WARNING);
  }

  public void error(Object message) {
    log(message, NotificationType.ERROR);
  }

  private void log(Object message, NotificationType type) {
    final StackTraceElement[] stackTrace = new Throwable().getStackTrace();
    String location = "";
    for (StackTraceElement e : stackTrace) {
      final String methodName = e.getMethodName();
      if ("run".equals(methodName)) {
        location = "(" + e.getFileName() + ":" + e.getLineNumber() + ") ";
        break;
      }
    }
    myEventLog.createNotification(location + String.valueOf(message), type).notify(myProject);
  }
}
