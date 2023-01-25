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
package com.intellij.util.containers;

import java.util.Iterator;

public class ConvertingIterator <Domain, Range> implements Iterator<Range> {
  private final Iterator<? extends Domain> myBaseIterator;
  private final Convertor<? super Domain, ? extends Range> myConvertor;

  public static class IdConvertor <T> implements Convertor<T, T> {
    @Override
    public T convert(T object) {
      return object;
    }
  }

  public ConvertingIterator(Iterator<? extends Domain> baseIterator, Convertor<? super Domain, ? extends Range> convertor) {
    myBaseIterator = baseIterator;
    myConvertor = convertor;
  }

  @Override
  public boolean hasNext() {
    return myBaseIterator.hasNext();
  }

  @Override
  public Range next() {
    return myConvertor.convert(myBaseIterator.next());
  }

  @Override
  public void remove() {
    myBaseIterator.remove();
  }

  public static <Domain, Intermediate, Range> Convertor<Domain, Range> composition(final Convertor<? super Domain, ? extends Intermediate> convertor1,
                                                                                   final Convertor<? super Intermediate, ? extends Range> convertor2) {
    return domain -> convertor2.convert(convertor1.convert(domain));
  }

  public static <Domain, Range> ConvertingIterator<Domain, Range> create(Iterator<? extends Domain> iterator, Convertor<? super Domain, ? extends Range> convertor) {
    return new ConvertingIterator<>(iterator, convertor);
  }
}
