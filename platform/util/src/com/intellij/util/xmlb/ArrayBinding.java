/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;

class ArrayBinding extends AbstractCollectionBinding  {

  public ArrayBinding(final Class<?> valueClass, final Accessor accessor) {
    super(valueClass.getComponentType(), Constants.ARRAY, accessor);
  }

  @Override
  @SuppressWarnings({"unchecked"})
  Object processResult(Collection result, Object target) {
    return result.toArray((Object[])Array.newInstance(getElementType(), result.size()));
  }

  @Override
  Iterable getIterable(Object o) {
    return o != null ? Arrays.asList((Object[])o) : null;
  }
}
