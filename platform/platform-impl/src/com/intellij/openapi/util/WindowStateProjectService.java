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
package com.intellij.openapi.util;

import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.impl.ProjectFrameBounds;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Sergey.Malenkov
 */
@State(
  name = "WindowStateProjectService",
  storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)}
)
final class WindowStateProjectService extends WindowStateServiceImpl {
  private final Project myProject;

  WindowStateProjectService(Project project) {
    myProject = project;
  }

  @Override
  Point getDefaultLocationFor(Object object, @NotNull String key) {
    Rectangle bounds = getDefaultBoundsFor(object, key);
    return bounds == null ? null : bounds.getLocation();
  }

  @Override
  Dimension getDefaultSizeFor(Object object, @NotNull String key) {
    Rectangle bounds = getDefaultBoundsFor(object, key);
    return bounds == null ? null : bounds.getSize();
  }

  @Override
  Rectangle getDefaultBoundsFor(Object object, @NotNull String key) {
    //  backward compatibility when this service is used instead of ProjectFrameBounds
    return !key.equals("ProjectFrameBounds") ? null : ProjectFrameBounds.getInstance(myProject).getBounds();
  }

  @Override
  boolean getDefaultMaximizedFor(Object object, @NotNull String key) {
    return false;
  }
}
