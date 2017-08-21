/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ChangeListsWriteOperations {
  @Nullable
  String setDefault(String name);
  boolean setReadOnly(String name, boolean value);
  @NotNull
  LocalChangeList addChangeList(@NotNull String name, @Nullable String comment, @Nullable Object data);
  boolean removeChangeList(@NotNull String name);
  @Nullable
  MultiMap<LocalChangeList, Change> moveChangesTo(String name, @NotNull Change[] changes);
  boolean editName(@NotNull String fromName, @NotNull String toName);
  @Nullable
  String editComment(@NotNull String fromName, @Nullable String newComment);
}
