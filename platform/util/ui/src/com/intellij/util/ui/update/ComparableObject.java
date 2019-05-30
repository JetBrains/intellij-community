// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.update;

import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

public interface ComparableObject {
  Object[] NONE = ArrayUtilRt.EMPTY_OBJECT_ARRAY;

  @NotNull
  Object[] getEqualityObjects();

  class Impl implements ComparableObject {
    private final Object[] myObjects;

    public Impl() {
      this(NONE);
    }

    public Impl(@NotNull Object... objects) {
      myObjects = objects;
    }

    @NotNull
    @Override
    public Object[] getEqualityObjects() {
      return myObjects;
    }

    @Override
    public final boolean equals(Object obj) {
      return ComparableObjectCheck.equals(this, obj);
    }

    @Override
    public final int hashCode() {
      return ComparableObjectCheck.hashCode(this, super.hashCode());
    }
  }
}
