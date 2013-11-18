/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.vcs.log;

import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * <p>This factory is used to create a particular {@link ContentRevision} instance which corresponds to the file and the commit hash.</p>
 * <p>Different VCS plugins may use different {@link ContentRevision} implementation, therefore we need to be able
 *    to retrieve a correct instance for a commit.</p>
 */
public abstract class ContentRevisionFactory {

  @NotNull
  public abstract ContentRevision createRevision(@NotNull VirtualFile file, @NotNull Hash hash);

  @NotNull
  public abstract ContentRevision createRevision(@NotNull VirtualFile root, @NotNull String path, @NotNull Hash hash);

}
