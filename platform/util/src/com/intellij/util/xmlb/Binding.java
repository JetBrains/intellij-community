// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import com.intellij.openapi.diagnostic.Logger;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.List;

public interface Binding {
  Logger LOG = Logger.getInstance(Binding.class);

  @Nullable
  Object serialize(@NotNull Object o, @Nullable Object context, @Nullable SerializationFilter filter);

  default boolean isBoundTo(@NotNull Element element) {
    return false;
  }

  default void init(@NotNull Type originalType, @NotNull Serializer serializer) {
  }

  @Nullable
  static Object deserializeList(@NotNull Binding binding, @Nullable Object context, @NotNull List<? extends Element> nodes) {
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

  Object deserializeUnsafe(Object context, @NotNull Element element);

  static void addContent(@NotNull final Element targetElement, final Object node) {
    if (node instanceof Content) {
      Content content = (Content)node;
      targetElement.addContent(content);
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
