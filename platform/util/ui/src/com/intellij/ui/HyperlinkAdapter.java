// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

public abstract class HyperlinkAdapter implements HyperlinkListener {
  @Override
  public final void hyperlinkUpdate(HyperlinkEvent e) {
    if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
      hyperlinkActivated(e);
    }
  }

  protected abstract void hyperlinkActivated(HyperlinkEvent e);
}