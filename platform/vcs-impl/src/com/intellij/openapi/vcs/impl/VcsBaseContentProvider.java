/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author irengrig
 */
public interface VcsBaseContentProvider {
  ExtensionPointName<VcsBaseContentProvider> EP_NAME = ExtensionPointName.create("com.intellij.vcs.baseContentProvider");

  @Nullable
  BaseContent getBaseRevision(@NotNull VirtualFile file);

  boolean isSupported(@NotNull VirtualFile file);

  interface BaseContent {
    @NotNull
    VcsRevisionNumber getRevisionNumber();

    @Nullable
    String loadContent();
  }
}
