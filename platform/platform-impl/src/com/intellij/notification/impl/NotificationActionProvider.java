// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.event.ActionEvent;

public interface NotificationActionProvider extends NotificationFullContent {
  Action @NotNull [] getActions(HyperlinkListener listener);

  final class Action extends AbstractAction {
    private final HyperlinkListener myListener;
    private final String myLink;
    private final boolean myDefaultAction;

    public Action(HyperlinkListener listener, String link, String name) {
      this(listener, link, name, false);
    }

    public Action(HyperlinkListener listener, String link, String name, boolean defaultAction) {
      super(name);
      myListener = listener;
      myLink = link;
      myDefaultAction = defaultAction;
    }

    public boolean isDefaultAction() {
      return myDefaultAction;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
      myListener.hyperlinkUpdate(new HyperlinkEvent(event.getSource(), HyperlinkEvent.EventType.ACTIVATED, null, myLink));
    }
  }
}
