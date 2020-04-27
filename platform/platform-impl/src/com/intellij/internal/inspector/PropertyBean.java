// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.inspector;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PropertyBean {
  public final String propertyName;
  public final Object propertyValue;
  public final boolean changed;

  public PropertyBean(@NotNull String name, @Nullable Object value) {
    this(name, value, false);
  }

  public PropertyBean(@NotNull String name, @Nullable Object value, boolean changed) {
    propertyName = name;
    propertyValue = value;
    this.changed = changed;
  }
}
