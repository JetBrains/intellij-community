// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import java.util.Iterator;

public final class ConvertingIterator <Domain, Range> implements Iterator<Range> {
  private final Iterator<? extends Domain> myBaseIterator;
  private final Convertor<? super Domain, ? extends Range> myConvertor;

  public static final class IdConvertor <T> implements Convertor<T, T> {
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
