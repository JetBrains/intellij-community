// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.XmlElement;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class NotNullDeserializeBinding implements Binding {
  @SuppressWarnings("LoggerInitializedWithForeignClass")
  protected static final Logger LOG = Logger.getInstance(Binding.class);

  public abstract @NotNull Object deserialize(@Nullable Object context, @NotNull Element element);

  public abstract @NotNull Object deserialize(@Nullable Object context, @NotNull XmlElement element);

  @Override
  public final Object deserializeUnsafe(Object context, @NotNull Element element) {
    return deserialize(context, element);
  }

  @Override
  public final Object deserializeUnsafe(Object context, @NotNull XmlElement element) {
    return deserialize(context, element);
  }
}
