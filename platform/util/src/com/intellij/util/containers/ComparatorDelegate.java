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

import java.util.Comparator;

public class ComparatorDelegate<Src, Tgt> implements Comparator<Src> {
  private final Convertor<Src, Tgt> myConvertor;
  private final Comparator<Tgt> myDelegate;

  public ComparatorDelegate(final Convertor<Src, Tgt> convertor, final Comparator<Tgt> delegate) {
    myConvertor = convertor;
    myDelegate = delegate;
  }

  @Override
  public int compare(final Src o1, final Src o2) {
    return myDelegate.compare(myConvertor.convert(o1), myConvertor.convert(o2));
  }
}
