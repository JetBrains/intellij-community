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
package com.intellij.util.ui;

import org.jetbrains.annotations.NotNull;

public abstract class CollectionItemEditor<T> {
  @NotNull
  /**
   * Class must have an empty constructor.
   */
  public abstract Class<? extends T> getItemClass();

  /**
   * Used for "copy" and "in place edit" actions.
   *
   * You must perform deep clone in case of "add" operation, but in case of "in place edit" you should copy only exposed (via column) properties.
   */
  public abstract T clone(@NotNull T item, boolean forInPlaceEditing);

  public boolean isRemovable(@NotNull T item) {
    return true;
  }

  public boolean isEmpty(@NotNull T item) {
    return false;
  }
}
