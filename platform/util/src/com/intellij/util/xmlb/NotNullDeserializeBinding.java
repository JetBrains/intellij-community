// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class NotNullDeserializeBinding implements Binding {
  @NotNull
  public abstract Object deserialize(@Nullable Object context, @NotNull Element element);

  @Override
  public final Object deserializeUnsafe(Object context, @NotNull Element element) {
    return deserialize(context, element);
  }
}
