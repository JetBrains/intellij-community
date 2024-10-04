/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.vcs.log.graph.utils.impl;

import com.intellij.vcs.log.graph.utils.IntList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class FullIntList implements IntList {

  public static FullIntList newInstance(@NotNull IntList delegateList) {
    int[] list = new int[delegateList.size()];
    for (int i = 0; i < list.length; i++) {
      list[i] = delegateList.get(i);
    }
    return new FullIntList(list);
  }

  public FullIntList(int[] list) {
    myList = list;
  }

  private final int[] myList;

  @Override
  public int size() {
    return myList.length;
  }

  @Override
  public int get(int index) {
    return myList[index];
  }
}
