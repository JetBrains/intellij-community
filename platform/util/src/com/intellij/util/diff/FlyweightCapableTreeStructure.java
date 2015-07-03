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

package com.intellij.util.diff;

import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public interface FlyweightCapableTreeStructure<T> {
  @NotNull
  T getRoot();

  @Nullable
  T getParent(@NotNull T node);

  @NotNull
  T prepareForGetChildren(@NotNull T node);

  int getChildren(@NotNull T parent, @NotNull Ref<T[]> into);

  void disposeChildren(T[] nodes, int count);

  @NotNull
  CharSequence toString(@NotNull T node);

  int getStartOffset(@NotNull T node);
  int getEndOffset(@NotNull T node);
}
