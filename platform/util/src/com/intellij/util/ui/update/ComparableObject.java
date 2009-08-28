/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.util.ui.update;

import com.intellij.util.ArrayUtil;

public interface ComparableObject {

  Object[] NONE = ArrayUtil.EMPTY_OBJECT_ARRAY;

  Object[] getEqualityObjects();

  class Impl implements ComparableObject {

    private Object[] myObjects;

    public Impl() {
      this(NONE);
    }

    public Impl(Object object) {
      this(new Object[] {object});
    }

    public Impl(Object[] objects) {
      myObjects = objects;
    }

    public Object[] getEqualityObjects() {
      return myObjects;
    }

    public final boolean equals(Object obj) {
      return ComparableObjectCheck.equals(this, obj);
    }

    public final int hashCode() {
      return ComparableObjectCheck.hashCode(this, super.hashCode());
    }
  }

}
