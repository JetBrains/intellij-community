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

import java.util.List;

public class XmlSerializerUtil {
  private XmlSerializerUtil() {
  }

  public static <T> void copyBean(@NotNull T from, @NotNull T to) {
    assert from.getClass().isAssignableFrom(to.getClass()) : "Beans of different classes specified: Cannot assign " +
                                                             from.getClass() + " to " + to.getClass();
    for (Accessor accessor : BeanBinding.getAccessors(to.getClass())) {
      accessor.write(to, accessor.read(from));
    }
  }

  public static <T> T createCopy(@NotNull T from) {
    try {
      final T to = (T)from.getClass().newInstance();
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
