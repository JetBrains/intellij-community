// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

public abstract class AncestorListenerAdapter implements AncestorListener {
  @Override
  public void ancestorAdded(AncestorEvent event) {
  }

  @Override
  public void ancestorRemoved(AncestorEvent event) {
  }

  @Override
  public void ancestorMoved(AncestorEvent event) {
  }
}