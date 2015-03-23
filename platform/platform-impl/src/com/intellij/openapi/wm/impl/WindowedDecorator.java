/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FrameWrapper;
import org.jetbrains.annotations.NotNull;

public final class WindowedDecorator extends FrameWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.impl.WindowedDecorator");

  private final Project myProject;

  WindowedDecorator(@NotNull Project project, @NotNull WindowInfoImpl info, @NotNull InternalDecorator internalDecorator) {
    super(project);
    myProject = project;
    setTitle(info.getId() + " - " + myProject.getName());
    setProject(project);
    setComponent(internalDecorator);
  }

  public Project getProject() {
    return myProject;
  }
}