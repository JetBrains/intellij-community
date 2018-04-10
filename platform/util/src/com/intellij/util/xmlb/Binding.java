// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import com.intellij.openapi.diagnostic.Logger;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.List;

public abstract class Binding {
  static final Logger LOG = Logger.getInstance(Binding.class);

  protected final MutableAccessor myAccessor;

  protected Binding(@Nullable MutableAccessor accessor) {
    myAccessor = accessor;
  }

  @NotNull
  public MutableAccessor getAccessor() {
    //noinspection ConstantConditions
    return myAccessor;
  }

  @Nullable
  public abstract Object serialize(@NotNull Object o, @Nullable Object context, @Nullable SerializationFilter filter);

  public boolean isBoundTo(@NotNull Element element) {
    return false;
  }

  public void init(@NotNull Type originalType, @NotNull Serializer serializer) {
  }

  @Nullable
  public static Object deserializeList(@NotNull Binding binding, @Nullable Object context, @NotNull List<Element> nodes) {
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

  public abstract Object deserializeUnsafe(Object context, @NotNull Element element);

  protected static void addContent(@NotNull final Element targetElement, final Object node) {
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
