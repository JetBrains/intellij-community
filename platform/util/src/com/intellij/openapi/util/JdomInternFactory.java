// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.util.containers.StringInterner;
import org.jdom.*;
import org.jetbrains.annotations.NotNull;

// don't intern CDATA - in most cases it is used for some unique large text (e.g. plugin description)
final class JdomInternFactory extends DefaultJDOMFactory {
  private final StringInterner stringInterner;

  JdomInternFactory(@NotNull StringInterner stringInterner) {
    this.stringInterner = stringInterner;
  }

  @Override
  public Attribute attribute(String name, String value, Namespace namespace) {
    return super.attribute(stringInterner.intern(name), stringInterner.intern(value), namespace);
  }

  @Override
  public Attribute attribute(String name, String value) {
    return super.attribute(stringInterner.intern(name), stringInterner.intern(value));
  }

  @Override
  public Attribute attribute(String name, String value, AttributeType type) {
    return super.attribute(stringInterner.intern(name), stringInterner.intern(value), type);
  }

  @Override
  public Attribute attribute(String name, String value, AttributeType type, Namespace namespace) {
    return super.attribute(stringInterner.intern(name), stringInterner.intern(value), type, namespace);
  }

  @Override
  public Text text(int line, int col, String text) {
    return super.text(line, col, stringInterner.intern(text));
  }

  @Override
  public Element element(int line, int col, String name, Namespace namespace) {
    return super.element(line, col, stringInterner.intern(name), namespace);
  }

  @Override
  public Element element(int line, int col, String name) {
    return super.element(line, col, stringInterner.intern(name));
  }

  @Override
  public Element element(int line, int col, String name, String uri) {
    return super.element(line, col, stringInterner.intern(name), uri);
  }

  @Override
  public Element element(int line, int col, String name, String prefix, String uri) {
    return super.element(line, col, stringInterner.intern(name), prefix, uri);
  }
}
