// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import com.intellij.util.xml.dom.XmlElement;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

interface MultiNodeBinding extends Binding {
  @Nullable Object deserializeList(@Nullable Object context, @NotNull List<Element> elements);

  @Nullable Object deserializeList2(@Nullable Object context, @NotNull List<XmlElement> elements);

  boolean isMulti();
}