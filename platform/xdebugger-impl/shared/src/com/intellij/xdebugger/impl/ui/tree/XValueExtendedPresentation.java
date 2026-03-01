// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.xdebugger.frame.presentation.XValuePresentation;

public abstract class XValueExtendedPresentation extends XValuePresentation {
  public boolean isModified() {
    return false;
  }
}
