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

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * A special reference to a commit, such as a branch or a tag.
 *
 * @author Kirill Likhodedov
 */
public interface VcsRef {

  /**
   * Returns the hash of the commit which this reference points to.
   */
  @NotNull
  Hash getCommitHash();

  /**
   * Returns the display name of the reference.
   */
  @NotNull
  String getName();

  /**
   * Returns the type of this reference. There can be different types across different VCS.
   */
  @NotNull
  VcsRefType getType();

  /**
   * Returns the VCS root this reference belongs to.
   */
  @NotNull
  VirtualFile getRoot();

}
