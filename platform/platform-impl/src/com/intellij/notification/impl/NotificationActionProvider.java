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

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

/**
 * @author Sergey.Malenkov
 */
public interface NotificationActionProvider {
  @NotNull
  Action[] getActions(HyperlinkListener listener);

  final class Action extends AbstractAction {
    private final HyperlinkListener myListener;
    private final String myLink;

    public Action(HyperlinkListener listener, String link, String name) {
      super(name);
      myListener = listener;
      myLink = link;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
      myListener.hyperlinkUpdate(new HyperlinkEvent(event.getSource(), HyperlinkEvent.EventType.ACTIVATED, null, myLink));
    }
  }
}
