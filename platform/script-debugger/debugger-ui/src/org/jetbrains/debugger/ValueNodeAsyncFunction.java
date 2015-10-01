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
package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncFunction;
import org.jetbrains.concurrency.Obsolescent;

public abstract class ValueNodeAsyncFunction<PARAM, RESULT> implements AsyncFunction<PARAM, RESULT>, Obsolescent {
  private final Obsolescent node;

  protected ValueNodeAsyncFunction(@NotNull Obsolescent node) {
    this.node = node;
  }

  @Override
  public final boolean isObsolete() {
    return node.isObsolete();
  }
}
