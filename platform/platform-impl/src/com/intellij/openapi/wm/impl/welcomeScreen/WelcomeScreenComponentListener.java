// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

@ApiStatus.Internal
public interface WelcomeScreenComponentListener {

  @Topic.AppLevel
  Topic<WelcomeScreenComponentListener> COMPONENT_CHANGED = new Topic<>("WelcomeScreenComponentChanged",
                                                                        WelcomeScreenComponentListener.class);

  void attachComponent(@NotNull Component componentToShow, @Nullable Runnable onDone);

  void detachComponent(@NotNull Component componentToDetach, @Nullable Runnable onDone);
}
