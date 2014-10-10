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
package com.intellij.vcs.log.data;

import org.jetbrains.annotations.NotNull;

enum CommitCountStage {

  INITIAL(5),
  FIRST_STEP(2000),
  ALL(Integer.MAX_VALUE);

  private final int myCount;

  CommitCountStage(int count) {
    myCount = count;
  }

  @NotNull
  CommitCountStage next() {
    CommitCountStage[] values = values();
    return ordinal() == values.length - 1 ? this : values[ordinal() + 1];
  }

  public int getCount() {
    return myCount;
  }
}
