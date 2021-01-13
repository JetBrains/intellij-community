// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
