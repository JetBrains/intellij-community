// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jdom.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Internal use only.
 */
@ApiStatus.Experimental
public interface SafeJdomFactory {
  @NotNull
  Element element(@NotNull String name, @Nullable Namespace namespace);

  @NotNull
  Attribute attribute(@NotNull String name, @NotNull String value, @Nullable AttributeType type, @Nullable Namespace namespace);

  @NotNull
  Text text(@NotNull String text, @NotNull Element parentElement);

  @NotNull
  CDATA cdata(@NotNull String text);

  class BaseSafeJdomFactory implements SafeJdomFactory {
    @NotNull
    @Override
    public Element element(@NotNull String name, @Nullable Namespace namespace) {
      Element element = new Element(name, namespace);
      if (namespace != null) {
        element.setNamespace(namespace);
      }
      return element;
    }

    @NotNull
    @Override
    public Attribute attribute(@NotNull String name, @NotNull String value, @Nullable AttributeType type, @Nullable Namespace namespace) {
      return new Attribute(name, value, type, namespace);
    }

    @NotNull
    @Override
    public Text text(@NotNull String text, @NotNull Element parentElement) {
      return new Text(text);
    }

    @NotNull
    @Override
    public CDATA cdata(@NotNull String text) {
      return new CDATA(text);
    }
  }
}
