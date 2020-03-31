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
package com.intellij.util;

/**
 * Please use {@link java.util.function.Consumer} instead
 */
public interface Consumer<T> {
  /**
   * @deprecated use {@link com.intellij.util.EmptyConsumer#getInstance()} instead
   */
  @Deprecated
  Consumer<Object> EMPTY_CONSUMER = new Consumer<Object>() {
    public void consume(Object t) { }
  };

  /**
   * @param t consequently takes value of each element of the set this processor is passed to for processing.
   * t is supposed to be a not-null value. If you need to pass {@code null}s to the consumer use {@link NullableConsumer} instead
   */
  void consume(T t);
}