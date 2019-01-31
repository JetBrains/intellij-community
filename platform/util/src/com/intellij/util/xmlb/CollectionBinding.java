// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import com.intellij.util.SmartList;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.ParameterizedType;
import java.util.*;

class CollectionBinding extends AbstractCollectionBinding  {
  CollectionBinding(@NotNull ParameterizedType type, @Nullable MutableAccessor accessor) {
    super(XmlSerializerImpl.typeToClass(type.getActualTypeArguments()[0]), accessor);
  }

  private static boolean isMutableCollection(@Nullable Object object) {
    if (object == Collections.emptyList() || object == Collections.emptySet()) {
      return false;
    }
    else if (object instanceof Collection) {
      String simpleName = object.getClass().getSimpleName();
      return !simpleName.equals("EmptyList") && !simpleName.startsWith("Unmodifiable") && !simpleName.equals("EmptySet");
    }
    else {
      return false;
    }
  }

  @NotNull
  @Override
  protected Object doDeserializeList(@Nullable Object context, @NotNull List<? extends Element> elements) {
    Collection result;
    boolean isContextMutable = isMutableCollection(context);
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

  @NotNull
  @Override
  protected String getCollectionTagName(@Nullable final Object target) {
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
