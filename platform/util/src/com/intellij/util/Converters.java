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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class Converters {
  private Converters() {}

  public static <K,V> List<V> convert(List<K> list, Function<K,V> fun) {
    final ArrayList<V> result = new ArrayList<V>(list.size());
    for (K k : list) {
      result.add(fun.fun(k));
    }
    return result;
  }

  @SuppressWarnings({"unchecked"})
  public static <K,V> V[] convert(K[] from, V[] to, Function<K,V> fun) {
    if (to.length < from.length) {
      to = (V[])Array.newInstance(to.getClass().getComponentType(), from.length);
    }
    for (int i = 0; i < from.length; i++) {
      to[i] = fun.fun(from[i]);
    }
    return to;
  }
}
