/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
* @author irengrig
*         Date: 4/7/11
*         Time: 6:43 PM
*/
public class GatheringContinuationContext implements ContinuationContext {
  private final List<TaskDescriptor> myList;

  public GatheringContinuationContext() {
    myList = new ArrayList<TaskDescriptor>();
  }

  public List<TaskDescriptor> getList() {
    return myList;
  }

  @Override
  public void cancelEverything() {
  }

  @Override
  public void cancelCurrent() {
  }

  @Override
  public <T extends Exception> void addExceptionHandler(Class<T> clazz, Consumer<T> consumer) {
  }

  @Override
  public boolean handleException(Exception e, boolean cancelEveryThing) {
    return false;
  }

  @Override
  public void throwDisaster(Object disaster, final Object cure) {
  }

  @Override
  public void keepExisting(Object disaster, Object cure) {
  }

  @Override
  public void next(TaskDescriptor... next) {
    myList.addAll(0, Arrays.asList(next));
  }

  @Override
  public void next(List<TaskDescriptor> next) {
    myList.addAll(0, next);
  }

  @Override
  public void last(TaskDescriptor... next) {
    myList.addAll(Arrays.asList(next));
  }

  @Override
  public void last(List<TaskDescriptor> next) {
    myList.addAll(next);
  }

  @Override
  public void after(@NotNull TaskDescriptor inQueue, TaskDescriptor... next) {
    int idx = -1;
    for (int i = 0; i < myList.size(); i++) {
      final TaskDescriptor descriptor = myList.get(i);
      if (inQueue == descriptor) {
        idx = i;
        break;
      }
    }
    assert idx != -1;
    myList.addAll(idx, Arrays.asList(next));
  }

  @Override
  public void suspend() {
  }

  @Override
  public void ping() {
  }

  @Override
  public void addNewTasksPatcher(@NotNull Consumer<TaskDescriptor> consumer) {
  }

  @Override
  public void removeNewTasksPatcher(@NotNull Consumer<TaskDescriptor> consumer) {
  }
}
