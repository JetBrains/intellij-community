// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public interface WelcomeScreenComponentListener {
  Topic<WelcomeScreenComponentListener> COMPONENT_CHANGED =
    Topic.create("WelcomeScreenComponentChanged", WelcomeScreenComponentListener.class);

  void attachComponent(@NotNull Component componentToShow, @Nullable Runnable onDone);

  void detachComponent(@NotNull Component componentToDetach, @Nullable Runnable onDone);
}
