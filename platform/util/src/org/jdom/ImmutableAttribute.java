// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jdom;

import org.jetbrains.annotations.NotNull;

final class ImmutableAttribute extends Attribute {
  ImmutableAttribute(@NotNull String name, @NotNull String value, AttributeType type, @NotNull Namespace namespace) {
    super.setName(name);
    super.setValue(value);
    super.setNamespace(namespace);
    super.setAttributeType(type);
  }

  @SuppressWarnings("MethodDoesntCallSuperMethod")
  @Override
  public Attribute clone() {
    Attribute attribute = new Attribute();
    attribute.name = getName();
    attribute.namespace = getNamespace();
    attribute.type = type;
    attribute.value = value;

    attribute.parent = null;
    return attribute;
  }

  @Override
  public Element getParent() {
    throw ImmutableElement.immutableError(this);
  }

  /////////////////////////////////////////////////////////
  @Override
  protected Attribute setParent(Element parent) {
    throw ImmutableElement.immutableError(this);
    //return null; // to be able to add this to the other element
  }

  @Override
  public Attribute detach() {
    throw ImmutableElement.immutableError(this);
  }

  @Override
  public Attribute setName(@NotNull String name) {
    throw ImmutableElement.immutableError(this);
  }

  @Override
  public Attribute setNamespace(Namespace namespace) {
    throw ImmutableElement.immutableError(this);
  }

  @Override
  public Attribute setValue(@NotNull String value) {
    throw ImmutableElement.immutableError(this);
  }

  @Override
  public Attribute setAttributeType(int type) {
    throw ImmutableElement.immutableError(this);
  }
}
