// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.actionSystem.TypedAction;

public class TypedActionImpl extends TypedAction {
  private final DefaultRawTypedHandler myDefaultRawTypedHandler;

  public TypedActionImpl() {
    myDefaultRawTypedHandler = new DefaultRawTypedHandler(this);
    setupRawHandler(myDefaultRawTypedHandler);
  }

  public DefaultRawTypedHandler getDefaultRawTypedHandler() {
    return myDefaultRawTypedHandler;
  }
}
