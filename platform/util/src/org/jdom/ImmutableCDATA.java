// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jdom;

import org.jetbrains.annotations.NotNull;

public final class ImmutableCDATA extends CDATA {
  ImmutableCDATA(@NotNull String str) {
    super.setText(str);
  }

  @Override
  public CDATA clone() {
    return new CDATA(true, value);
  }

  @Override
  public Element getParent() {
    throw ImmutableElement.immutableError(this);
  }

  //////////////////////////////////////////////////////////////////////////
  @Override
  public CDATA setText(String str) {
    throw ImmutableElement.immutableError(this);
  }

  @Override
  public void append(String str) {
    throw ImmutableElement.immutableError(this);
  }

  @Override
  public void append(Text text) {
    throw ImmutableElement.immutableError(this);
  }

  @Override
  public CDATA detach() {
    throw ImmutableElement.immutableError(this);
  }

  @Override
  protected CDATA setParent(Parent parent) {
    throw ImmutableElement.immutableError(this);
    //return null; // to be able to add this to the other element
  }
}
