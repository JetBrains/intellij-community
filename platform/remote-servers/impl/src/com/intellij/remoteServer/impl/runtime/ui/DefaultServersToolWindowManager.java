/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.remoteServer.impl.runtime.ui;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class DefaultServersToolWindowManager extends ServersToolWindowManager {

  public static final String WINDOW_ID = "Application Servers";

  public DefaultServersToolWindowManager(Project project) {
    super(project, WINDOW_ID);
  }

  @NotNull
  public String getComponentName() {
    return "ServersToolWindowManager";
  }

  @NotNull
  @Override
  protected ServersToolWindowFactory getFactory() {
    return new DefaultServersToolWindowFactory();
  }
}
