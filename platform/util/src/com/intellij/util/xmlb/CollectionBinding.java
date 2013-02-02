/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

class CollectionBinding extends AbstractCollectionBinding  {
  public CollectionBinding(ParameterizedType type, final Accessor accessor) {
    super(getComponentClass(type), Constants.COLLECTION, accessor);
  }

  private static Class getComponentClass(ParameterizedType type) {
    Type arg = type.getActualTypeArguments()[0];
    if (arg instanceof ParameterizedType) {
      return (Class)((ParameterizedType)arg).getRawType();
    }
    return (Class)arg;
  }


  Object processResult(Collection result, Object target) {
    if (myAccessor == null) return result;
    
    assert target != null: "Null target in " + myAccessor;
    assert target instanceof Collection : "Wrong target: " + target.getClass() + " in " + myAccessor;
    Collection c = (Collection)target;
    c.clear();
    //noinspection unchecked
    c.addAll(result);

    return target;
  }

  Iterable getIterable(Object o) {
    if (o instanceof Set) {
      return new TreeSet((Set)o);
    }
    return (Collection)o;
  }

  protected String getCollectionTagName(final Object target) {
    if (target instanceof Set) {
      return Constants.SET;
    }
    else if (target instanceof List) {
      return Constants.LIST;
    }
    return super.getCollectionTagName(target);
  }

  protected Collection createCollection(final String tagName) {
    if (tagName.equals(Constants.SET)) return new HashSet();
    if (tagName.equals(Constants.LIST)) return new ArrayList();
    return super.createCollection(tagName);
  }
}
