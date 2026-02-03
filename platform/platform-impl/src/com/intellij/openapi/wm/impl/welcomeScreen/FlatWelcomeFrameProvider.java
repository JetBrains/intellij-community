// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WelcomeFrameProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public final class FlatWelcomeFrameProvider implements WelcomeFrameProvider {
  @Override
  public @NotNull IdeFrame createFrame() {
    return new FlatWelcomeFrame();
  }
}
