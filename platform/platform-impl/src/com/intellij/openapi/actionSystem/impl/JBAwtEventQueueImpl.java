// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.JBAwtEventQueue;

import java.awt.event.MouseEvent;

public class JBAwtEventQueueImpl extends JBAwtEventQueue {
  @Override
  public void blockNextEvents(MouseEvent e) {
    IdeEventQueue.getInstance().blockNextEvents(e);
  }
}
