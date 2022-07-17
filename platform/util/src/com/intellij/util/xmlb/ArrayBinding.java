// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import com.intellij.serialization.MutableAccessor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xml.dom.XmlElement;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class ArrayBinding extends AbstractCollectionBinding  {
  ArrayBinding(@NotNull Class<?> valueClass, @Nullable MutableAccessor accessor) {
    super(valueClass.getComponentType(), accessor);
  }

  @Override
  protected @NotNull String getCollectionTagName(@Nullable Object target) {
    return "array";
  }

  @Override
  protected @NotNull Object doDeserializeList(@Nullable Object context, @NotNull List<Element> elements) {
    int size = elements.size();
    Object[] result = ArrayUtil.newArray(itemType, size);
    for (int i = 0; i < size; i++) {
      result[i] = deserializeItem(elements.get(i), context);
    }
    return result;
  }

  @Override
  protected @NotNull Object doDeserializeList2(@Nullable Object context, @NotNull List<XmlElement> elements) {
    int size = elements.size();
    Object[] result = ArrayUtil.newArray(itemType, size);
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
