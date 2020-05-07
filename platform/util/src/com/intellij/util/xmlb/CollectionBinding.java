// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import com.intellij.serialization.ClassUtil;
import com.intellij.serialization.MutableAccessor;
import com.intellij.util.SmartList;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.ParameterizedType;
import java.util.*;

final class CollectionBinding extends AbstractCollectionBinding  {
  CollectionBinding(@NotNull ParameterizedType type, @Nullable MutableAccessor accessor) {
    super(ClassUtil.typeToClass(type.getActualTypeArguments()[0]), accessor);
  }

  @Override
  protected @NotNull Object doDeserializeList(@Nullable Object context, @NotNull List<? extends Element> elements) {
    Collection result;
    boolean isContextMutable = context != null && ClassUtil.isMutableCollection(context);
    if (isContextMutable) {
      result = (Collection)context;
      result.clear();
    }
    else {
      result = context instanceof Set ? new HashSet() : new SmartList();
    }

    for (Element node : elements) {
      //noinspection unchecked
      result.add(deserializeItem(node, context));
    }

    return result;
  }

  @SuppressWarnings("unchecked")
  @NotNull
  @Override
  Collection<Object> getIterable(@NotNull Object o) {
    if (isSortOrderedSet() && o instanceof LinkedHashSet) {
      return (Collection<Object>)o;
    }
    return o instanceof Set ? new TreeSet((Set)o) : (Collection<Object>)o;
  }

  @Override
  protected @NotNull String getCollectionTagName(final @Nullable Object target) {
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
