// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public interface KeymapListener {

  @Topic.AppLevel
  Topic<KeymapListener> CHANGE_TOPIC = new Topic<>("KEYMAP_CHANGED", KeymapListener.class);

  void quickListRenamed(@NotNull QuickList oldQuickList, @NotNull QuickList newQuickList);

  void processCurrentKeymapChanged(QuickList @NotNull [] ids);

  void processCurrentKeymapChanged();
}
