/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import java.lang.reflect.ParameterizedType;
import java.util.*;

class CollectionBinding extends AbstractCollectionBinding  {
  public CollectionBinding(@NotNull ParameterizedType type, @Nullable MutableAccessor accessor) {
    super(XmlSerializerImpl.typeToClass(type.getActualTypeArguments()[0]), accessor);
  }
  
  @Override
  Object processResult(Collection result, Object target) {
    if (myAccessor == null) {
      return result;
    }
    
    assert target != null: "Null target in " + myAccessor;
    assert target instanceof Collection : "Wrong target: " + target.getClass() + " in " + myAccessor;
    Collection c = (Collection)target;
    c.clear();
    //noinspection unchecked
    c.addAll(result);

    return target;
  }

  @NotNull
  @Override
  Collection<Object> getIterable(@NotNull Object o) {
    //noinspection unchecked
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
