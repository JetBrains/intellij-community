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
package com.intellij.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BufferedListConsumer<T> implements Consumer<List<T>> {
  private final int mySize;
  private final List<T> myBuffer;
  private final Consumer<List<T>> myConsumer;

  public BufferedListConsumer(int size, Consumer<List<T>> consumer) {
    mySize = size;
    myBuffer = new ArrayList<T>();
    myConsumer = consumer;
  }

  public void consumeOne(final T t) {
    consume(Collections.singletonList(t));
  }

  public void consume(List<T> list) {
    myBuffer.addAll(list);
    if (mySize <= myBuffer.size()) {
      myConsumer.consume(new ArrayList<T>(myBuffer));
      myBuffer.clear();
    }
  }

  public void flush() {
    if (! myBuffer.isEmpty()) {
      myConsumer.consume(new ArrayList<T>(myBuffer));
      myBuffer.clear();
    }
  }
}
