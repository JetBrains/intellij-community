// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

public abstract class HyperlinkAdapter implements HyperlinkListener {

  @Override
  public final void hyperlinkUpdate(HyperlinkEvent e) {
    if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
      hyperlinkActivated(e);
    }
  }

  protected abstract void hyperlinkActivated(@NotNull HyperlinkEvent e);
}