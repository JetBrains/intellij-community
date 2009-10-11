/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util.containers;

import com.intellij.openapi.util.NotNullLazyValue;

import java.util.Iterator;

/**
 * @author peter
*/
public class LazyIterator<T> implements Iterator<T> {
  private final NotNullLazyValue<Iterator<T>> myLazyValue;

  public LazyIterator(final NotNullLazyValue<Iterator<T>> lazyIterator) {
    myLazyValue = lazyIterator;
  }

  public boolean hasNext() {
    return myLazyValue.getValue().hasNext();
  }

  public T next() {
    return myLazyValue.getValue().next();
  }

  public void remove() {
    myLazyValue.getValue().remove();
  }
}
