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

import java.util.Iterator;

/**
 * @author dsl
 */
public class ConvertingIterator <Domain, Range> implements Iterator<Range> {
  private final Iterator<Domain> myBaseIterator;
  private final Convertor<Domain, Range> myConvertor;

  public static class IdConvertor <T> implements Convertor<T, T> {
    public T convert(T object) {
      return object;
    }
  }

  public ConvertingIterator(Iterator<Domain> baseIterator, Convertor<Domain, Range> convertor) {
    myBaseIterator = baseIterator;
    myConvertor = convertor;
  }

  public boolean hasNext() {
    return myBaseIterator.hasNext();
  }

  public Range next() {
    return myConvertor.convert(myBaseIterator.next());
  }

  public void remove() {
    myBaseIterator.remove();
  }

  public static <Domain, Intermediate, Range> Convertor<Domain, Range> composition(final Convertor<Domain, Intermediate> convertor1,
                                                                                   final Convertor<Intermediate, Range> convertor2) {
    return new Convertor<Domain, Range>() {
      public Range convert(Domain domain) {
        return convertor2.convert(convertor1.convert(domain));
      }
    };
  }

  public static <Domain, Range> ConvertingIterator<Domain, Range>
    create(Iterator<Domain> iterator, Convertor<Domain, Range> convertor) {
    return new ConvertingIterator<Domain, Range>(iterator, convertor);
  }
}
