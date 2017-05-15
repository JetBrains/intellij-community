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

package com.intellij.lang.ant.config;

import com.intellij.lang.ant.config.execution.AntBuildMessageView;
import com.intellij.lang.ant.config.execution.AntExecutionListener;
import com.intellij.openapi.project.Project;

public abstract class AntBuildListener {

  public enum STATE {FINISHED_SUCCESSFULLY, ABORTED, FAILED_TO_RUN}

  public static final AntBuildListener NULL = new AntBuildListener() {
    @Override
    public void onBuildFinished(STATE state, int errorCount) { }
  };

  public final void buildFinished(Project project, STATE state, int errorCount) {
    final AntExecutionListener[] antExecutionListeners = AntExecutionListener.EP_NAME.getExtensions();
    for (AntExecutionListener listener: antExecutionListeners) {
      listener.buildFinished(project, state, errorCount);
    }
    onBuildFinished(state, errorCount);
  }

  public final void beforeBuildStart(AntBuildMessageView view) {
    final AntExecutionListener[] antExecutionListeners = AntExecutionListener.EP_NAME.getExtensions();
    for (AntExecutionListener listener: antExecutionListeners) {
      listener.buildStarted(view);
    }
  }

  public abstract void onBuildFinished(STATE state, int errorCount);
}
