// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb;

import com.intellij.util.xml.dom.XmlElement;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public interface MultiNodeBinding extends Binding {
  @Nullable Object deserializeJdomList(@Nullable Object context, @NotNull List<Element> elements);

  @Nullable Object deserializeList(@Nullable Object context, @NotNull List<XmlElement> elements);

  boolean isMulti();
}