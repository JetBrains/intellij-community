// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jdom.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface SafeJdomFactory {
  @NotNull Element element(@NotNull String name, @Nullable Namespace namespace);

  @NotNull Attribute attribute(@NotNull String name, @NotNull String value, @Nullable Namespace namespace);

  @NotNull Text text(@NotNull String text);

  @NotNull CDATA cdata(@NotNull String text);

  final class BaseSafeJdomFactory implements SafeJdomFactory {
    @Override
    public @NotNull Element element(@NotNull String name, @Nullable Namespace namespace) {
      Element element = new Element(name, namespace);
      if (namespace != null) {
        element.setNamespace(namespace);
      }
      return element;
    }

    @Override
    public @NotNull Attribute attribute(@NotNull String name, @NotNull String value, @Nullable Namespace namespace) {
      return new Attribute(name, value, AttributeType.UNDECLARED, namespace);
    }

    @Override
    public @NotNull Text text(@NotNull String text) {
      return new Text(text);
    }

    @Override
    public @NotNull CDATA cdata(@NotNull String text) {
      return new CDATA(text);
    }
  }
}
