// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

class ArrayBinding extends AbstractCollectionBinding  {
  public ArrayBinding(@NotNull Class<?> valueClass, @Nullable MutableAccessor accessor) {
    super(valueClass.getComponentType(), accessor);
  }

  @NotNull
  @Override
  protected String getCollectionTagName(@Nullable Object target) {
    return "array";
  }

  @NotNull
  protected Object doDeserializeList(@Nullable Object context, @NotNull List<Element> elements) {
    int size = elements.size();
    Object[] result = (Object[])Array.newInstance(itemType, size);
    for (int i = 0; i < size; i++) {
      result[i] = deserializeItem(elements.get(i), context);
    }
    return result;
  }

  @NotNull
  @Override
  Collection<Object> getIterable(@NotNull Object o) {
    Object[] list = (Object[])o;
    return list.length == 0 ? Collections.emptyList() : Arrays.asList(list);
  }
}
