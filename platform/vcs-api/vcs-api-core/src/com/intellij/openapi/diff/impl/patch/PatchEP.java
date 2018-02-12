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
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vcs.changes.CommitContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author irengrig
 */
public interface PatchEP {
  ExtensionPointName<PatchEP> EP_NAME = ExtensionPointName.create("com.intellij.patch.extension");
  @NotNull
  String getName();
  /**
   * @param path - before path, if exist, otherwise after path
   * @param commitContext
   */
  @Nullable
  CharSequence provideContent(@NotNull final String path, CommitContext commitContext);
  /**
   * @param path - before path, if exist, otherwise after path
   * @param commitContext
   * @deprecated it's better not to use PatchEP at all
   */
  @Deprecated
  void consumeContent(@NotNull final String path, @NotNull final CharSequence content, @Nullable CommitContext commitContext);
  /**
   * @param path - before path, if exist, otherwise after path
   * @param commitContext
   */
  void consumeContentBeforePatchApplied(@NotNull final String path,
                                        @NotNull final CharSequence content,
                                        @Nullable CommitContext commitContext);
}
