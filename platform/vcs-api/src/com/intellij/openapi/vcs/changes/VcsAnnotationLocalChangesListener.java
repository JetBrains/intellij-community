/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @see com.intellij.openapi.vcs.changes.VcsAnnotationRefresher
 */
public interface VcsAnnotationLocalChangesListener {
  void registerAnnotation(@NotNull FileAnnotation annotation);

  void unregisterAnnotation(@NotNull FileAnnotation annotation);

  /**
   * @deprecated Use {@link #registerAnnotation(FileAnnotation)}
   */
  @Deprecated
  default void registerAnnotation(@NotNull VirtualFile file, @NotNull FileAnnotation annotation) {
    registerAnnotation(annotation);
  }

  /**
   * @deprecated Use {@link #unregisterAnnotation(FileAnnotation)}
   */
  @Deprecated
  default void unregisterAnnotation(@NotNull VirtualFile file, @NotNull FileAnnotation annotation) {
    unregisterAnnotation(annotation);
  }

  void reloadAnnotations();

  void reloadAnnotationsForVcs(@NotNull VcsKey key);

  /**
   * @param vcsKey pass 'null' to refresh annotations for all vcses
   */
  void invalidateAnnotationsFor(@NotNull VirtualFile file, @Nullable VcsKey vcsKey);
}
