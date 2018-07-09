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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ChangeListModification {
  LocalChangeList addChangeList(@NotNull String name, @Nullable final String comment);

  void setDefaultChangeList(@NotNull String name);
  void setDefaultChangeList(@NotNull LocalChangeList list);

  void removeChangeList(@NotNull String name);
  void removeChangeList(@NotNull LocalChangeList list);

  void moveChangesTo(@NotNull LocalChangeList list, @NotNull Change... changes);

  /**
   * Prohibit changelist deletion or rename until Project is closed
   */
  boolean setReadOnly(@NotNull String name, final boolean value);

  boolean editName(@NotNull String fromName, @NotNull String toName);
  @Nullable
  String editComment(@NotNull String fromName, final String newComment);
}
