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
package com.intellij.testFramework.fixtures;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author Dmitry Avdeev
 */
public interface TempDirTestFixture extends IdeaTestFixture {
  @NotNull
  VirtualFile copyAll(@NotNull String dataDir, @NotNull String targetDir);

  @NotNull
  VirtualFile copyAll(@NotNull String dataDir, @NotNull String targetDir, @NotNull VirtualFileFilter filter);

  @NotNull
  String getTempDirPath();

  @Nullable
  VirtualFile getFile(@NotNull String path);

  @NotNull
  VirtualFile createFile(@NotNull String name);

  @NotNull
  VirtualFile findOrCreateDir(@NotNull String name) throws IOException;

  @NotNull
  VirtualFile createFile(@NotNull String name, @NotNull String text) throws IOException;
}