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
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public abstract class PushedFilePropertiesUpdater {
  @NotNull
  public static PushedFilePropertiesUpdater getInstance(Project project) {
    return project.getComponent(PushedFilePropertiesUpdater.class);
  }

  public abstract void initializeProperties();
  public abstract void pushAll(final FilePropertyPusher... pushers);
  public abstract void filePropertiesChanged(@NotNull final VirtualFile file);
  public abstract void pushAllPropertiesNow();
  public abstract <T> void findAndUpdateValue(final VirtualFile fileOrDir, final FilePropertyPusher<T> pusher, final T moduleValue);
}
