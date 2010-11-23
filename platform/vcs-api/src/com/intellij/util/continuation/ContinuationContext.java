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

import com.intellij.openapi.vcs.CalledInAny;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public interface ContinuationContext {
  @CalledInAny
  void next(TaskDescriptor... next);
  @CalledInAny
  void next(List<TaskDescriptor> next);
  @CalledInAny
  void last(TaskDescriptor... next);
  @CalledInAny
  void last(List<TaskDescriptor> next);
  @CalledInAny
  void cancelEverything();

  void suspend();
  void ping();

  class GatheringContinuationContext implements ContinuationContext {
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
    public void suspend() {
    }

    @Override
    public void ping() {
    }
  }
}
