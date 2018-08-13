// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl.actionholder;

import com.intellij.openapi.actionSystem.AnAction;

class IdActionRef<T extends AnAction> extends ActionRef<T> {
  private final String myId;

  public IdActionRef(String id) {
    myId = id;
  }

  @Override
  public T getAction() {
    T action = (T)getManager().getAction(myId);
    if (action != null) return action;
    throw new IllegalStateException("There's no registered action with id=" + myId);
  }
}
