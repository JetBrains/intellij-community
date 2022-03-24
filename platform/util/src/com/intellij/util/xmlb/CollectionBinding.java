// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import com.intellij.serialization.ClassUtil;
import com.intellij.serialization.MutableAccessor;
import com.intellij.util.xml.dom.XmlElement;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.ParameterizedType;
import java.util.*;

final class CollectionBinding extends AbstractCollectionBinding  {
  CollectionBinding(@NotNull ParameterizedType type, @Nullable MutableAccessor accessor) {
    super(ClassUtil.typeToClass(type.getActualTypeArguments()[0]), accessor);
  }

  @SuppressWarnings("DuplicatedCode")
  @Override
  protected @NotNull Object doDeserializeList(@Nullable Object context, @NotNull List<Element> elements) {
    Collection<Object> result;
    boolean isContextMutable = context != null && ClassUtil.isMutableCollection(context);
    if (isContextMutable) {
      //noinspection unchecked
      result = (Collection<Object>)context;
      result.clear();
    }
    else {
      result = context instanceof Set ? new HashSet<>() : new ArrayList<>();
    }

    for (Element node : elements) {
      result.add(deserializeItem(node, context));
    }

    return result;
  }

  @SuppressWarnings("DuplicatedCode")
  @Override
  protected @NotNull Object doDeserializeList2(@Nullable Object context, @NotNull List<XmlElement> elements) {
    Collection<Object> result;
    boolean isContextMutable = context != null && ClassUtil.isMutableCollection(context);
    if (isContextMutable) {
      //noinspection unchecked
      result = (Collection<Object>)context;
      result.clear();
    }
    else {
      result = context instanceof Set ? new HashSet<>() : new ArrayList<>();
    }

    for (XmlElement node : elements) {
      result.add(deserializeItem(node, context));
    }

    return result;
  }

  @NotNull
  @Override
  Collection<?> getIterable(@NotNull Object o) {
    Collection<?> collection = (Collection<?>)o;
    if (collection.size() < 2 || ((isSortOrderedSet() && o instanceof LinkedHashSet)) || o instanceof SortedSet) {
      // no need to sort
      return collection;
    }
    else if (o instanceof Set) {
      List<?> result = new ArrayList<>(collection);
      result.sort(null);
      return result;
    }
    else {
      return collection;
    }
  }

  @Override
  protected @NotNull String getCollectionTagName(@Nullable Object target) {
    if (target instanceof Set) {
      return Constants.SET;
    }
    else if (target instanceof List) {
      return Constants.LIST;
    }
    else {
      return "collection";
    }
  }
}
