// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb;

import com.intellij.util.XmlElement;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.List;

public interface Binding {
  @Nullable Object serialize(@NotNull Object o, @Nullable Object context, @Nullable SerializationFilter filter);

  boolean isBoundTo(@NotNull Element element);

  boolean isBoundTo(@NotNull XmlElement element);

  default void init(@NotNull Type originalType, @NotNull Serializer serializer) {
  }

  static @Nullable Object deserializeList(@NotNull Binding binding, @Nullable Object context, @NotNull List<Element> nodes) {
    if (binding instanceof MultiNodeBinding) {
      return ((MultiNodeBinding)binding).deserializeList(context, nodes);
    }
    else {
      if (nodes.size() == 1) {
        return binding.deserializeUnsafe(context, nodes.get(0));
      }
      else if (nodes.isEmpty()) {
        return null;
      }
      else {
        throw new AssertionError("Duplicate data for " + binding + " will be ignored");
      }
    }
  }

  static @Nullable Object deserializeList2(@NotNull Binding binding, @Nullable Object context, @NotNull List<XmlElement> nodes) {
    if (binding instanceof MultiNodeBinding) {
      return ((MultiNodeBinding)binding).deserializeList2(context, nodes);
    }
    else {
      if (nodes.size() == 1) {
        return binding.deserializeUnsafe(context, nodes.get(0));
      }
      else if (nodes.isEmpty()) {
        return null;
      }
      else {
        throw new AssertionError("Duplicate data for " + binding + " will be ignored");
      }
    }
  }

  Object deserializeUnsafe(Object context, @NotNull Element element);

  Object deserializeUnsafe(Object context, @NotNull XmlElement element);

  static void addContent(@NotNull Element targetElement, Object node) {
    if (node instanceof Content) {
      targetElement.addContent((Content)node);
    }
    else if (node instanceof List) {
      //noinspection unchecked
      targetElement.addContent((List)node);
    }
    else {
      throw new IllegalArgumentException("Wrong node: " + node);
    }
  }
}
