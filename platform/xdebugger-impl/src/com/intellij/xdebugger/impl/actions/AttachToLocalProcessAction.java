// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions;

/**
 * @deprecated Use {@link AttachToProcessAction}
 */
@SuppressWarnings("ALL")
public class AttachToLocalProcessAction extends AttachToProcessAction {
  @Override
  public boolean isDumbAware() {
    return true;
  }
}
