// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Lobas
 */
public interface WelcomeFrameUpdater {
  void showPluginUpdates(@NotNull Runnable callback);

  void hidePluginUpdates();
}