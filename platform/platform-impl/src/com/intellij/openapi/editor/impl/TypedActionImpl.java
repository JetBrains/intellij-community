// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.actionSystem.TypedAction;

public final class TypedActionImpl extends TypedAction {
  private final DefaultRawTypedHandler myDefaultRawTypedHandler;

  public TypedActionImpl() {
    myDefaultRawTypedHandler = new DefaultRawTypedHandler(this);
    setupRawHandler(myDefaultRawTypedHandler);
  }

  public DefaultRawTypedHandler getDefaultRawTypedHandler() {
    return myDefaultRawTypedHandler;
  }
}
