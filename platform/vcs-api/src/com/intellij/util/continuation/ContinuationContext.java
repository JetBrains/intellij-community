/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.util.continuation;

import org.jetbrains.annotations.CalledInAny;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ContinuationContext extends ContinuationPause {
  @CalledInAny
  void next(TaskDescriptor... next);
  @CalledInAny
  void next(List<TaskDescriptor> next);
  @CalledInAny
  void last(TaskDescriptor... next);
  @CalledInAny
  void last(List<TaskDescriptor> next);
  @CalledInAny
  void after(@NotNull TaskDescriptor inQueue, TaskDescriptor... next);

  @CalledInAny
  void cancelEverything();
  @CalledInAny
  void cancelCurrent();

  <T extends Exception> void addExceptionHandler(final Class<T> clazz, final Consumer<T> consumer);
  boolean handleException(final Exception e, boolean cancelEveryThing);

  void keepExisting(final Object disaster, final Object cure);
  void throwDisaster(final Object disaster, final Object cure);

  void removeNewTasksPatcher(@NotNull final Consumer<TaskDescriptor> consumer);
  void addNewTasksPatcher(@NotNull final Consumer<TaskDescriptor> consumer);
}
