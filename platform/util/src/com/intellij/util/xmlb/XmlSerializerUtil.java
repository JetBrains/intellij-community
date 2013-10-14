/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.util.xmlb;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class XmlSerializerUtil {
  private XmlSerializerUtil() {
  }

  @NotNull
  public static <F, T extends F> T copyBean(@NotNull F from, @NotNull T to) {
    //noinspection unchecked,RedundantCast
    return copyBean(from, to, (Class<F>) from.getClass());
  }

  @NotNull
  public static <B, F extends B, T extends B> T copyBean(@NotNull F from, @NotNull T to, @NotNull Class<B> asClass) {
    for (Accessor accessor : BeanBinding.getAccessors(asClass)) {
      accessor.write(to, accessor.read(from));
    }

    return to;
  }

  @Nullable
  public static <F, T extends F> T mergeBeans(@NotNull F from, @NotNull T to) {
    //noinspection unchecked,RedundantCast
    return mergeBeans(from, to, (Class<F>) from.getClass());
  }

  @Nullable
  public static <B, F extends B, T extends B> T mergeBeans(@NotNull F from, @NotNull T to, @NotNull Class<B> asClass) {
    T copy = createCopy(to);

    if (copy == null) return null;

    return copyBean(from, copy, asClass);
  }

  @Nullable
  public static <T> T createCopy(@NotNull T from) {
    try {
      @SuppressWarnings("unchecked")
      T to = (T)from.getClass().newInstance();
      copyBean(from, to);
      return to;
    }
    catch (Exception ignored) {
    }
    return null;
  }

  public static List<Accessor> getAccessors(Class aClass) {
    return BeanBinding.getAccessors(aClass);
  }
}
