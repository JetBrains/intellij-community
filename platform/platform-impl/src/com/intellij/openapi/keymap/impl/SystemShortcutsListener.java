// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl;

import com.intellij.util.messages.Topic;

public interface SystemShortcutsListener {
  @Topic.AppLevel
  Topic<SystemShortcutsListener> CHANGE_TOPIC = new Topic<>("SYSTEM_SHORTCUTS_CHANGED", SystemShortcutsListener.class);

  void processSystemShortcutsChanged();
}
