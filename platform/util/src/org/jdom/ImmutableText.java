// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jdom;

import org.jetbrains.annotations.NotNull;

public class ImmutableText extends Text {
  ImmutableText(@NotNull String str) {
    super.setText(str);
  }

  @SuppressWarnings("MethodDoesntCallSuperMethod")
  @Override
  public Text clone() {
    Text text = new Text();
    text.value = value;
    return text;
  }

  @Override
  public Element getParent() {
    throw ImmutableElement.immutableError(this);
  }

  //////////////////////////////////////////////////////////////////////////
  @Override
  public Text setText(String str) {
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
  public Text detach() {
    throw ImmutableElement.immutableError(this);
  }

  @Override
  protected Text setParent(Parent parent) {
    throw ImmutableElement.immutableError(this);
    //return null; // to be able to add this to the other element
  }
}
