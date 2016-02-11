/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.notification.impl;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.event.ActionEvent;

/**
 * @author Sergey.Malenkov
 */
public interface NotificationActionProvider {
  @NotNull
  Action[] getActions(HyperlinkListener listener);

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
