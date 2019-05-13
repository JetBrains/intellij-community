/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class SynchronizedCollectConsumer<T> extends CollectConsumer<T> {
  public SynchronizedCollectConsumer(@NotNull Collection<T> result) {
    super(result);
  }
  public SynchronizedCollectConsumer() {
    super();
  }

  @Override
  public synchronized void consume(T t) {
    super.consume(t);
  }

  @NotNull
  @Override
  public synchronized Collection<T> getResult() {
    return super.getResult();
  }
}
