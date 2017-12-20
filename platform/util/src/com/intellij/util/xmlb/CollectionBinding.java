/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.util.xmlb;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.ParameterizedType;
import java.util.*;

class CollectionBinding extends AbstractCollectionBinding  {
  public CollectionBinding(@NotNull ParameterizedType type, @Nullable MutableAccessor accessor) {
    super(XmlSerializerImpl.typeToClass(type.getActualTypeArguments()[0]), accessor);
  }
  
  @Override
  @NotNull
  Object processResult(@NotNull Collection result, @Nullable Object target) {
    if (myAccessor == null || target == null) {
      return result;
    }
    
    assert target instanceof Collection : "Wrong target: " + target.getClass() + " in " + myAccessor;
    Collection c = (Collection)target;
    c.clear();
    //noinspection unchecked
    c.addAll(result);

    return target;
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

  @Override
  protected Collection createCollection(@NotNull String tagName) {
    return tagName.equals(Constants.SET) ? new HashSet() : super.createCollection(tagName);
  }
}
