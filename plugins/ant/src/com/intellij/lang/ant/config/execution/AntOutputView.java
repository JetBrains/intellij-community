/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.ant.config.execution;

import com.intellij.openapi.actionSystem.DataSink;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface AntOutputView {
  String getId();

  Object addMessage(AntMessage message);
  void addJavacMessage(AntMessage message, String url);
  void addException(AntMessage exception, boolean showFullTrace);
  void startBuild(AntMessage message);
  void startTarget(AntMessage message);
  void startTask(AntMessage message);
  void finishBuild(@Nls String messageText);
  void finishTarget();
  void finishTask();

  void uiDataSnapshot(@NotNull DataSink sink);

  void buildFailed(AntMessage message);

  JComponent getComponent();
}

