// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import org.jetbrains.annotations.Nls;

public final class Html {

  private final @Nls String myText;
  private boolean myKeepFont = false;

  public Html(@Nls String text) {
    myText = text;
  }

  public @Nls String getText() {
    return myText;
  }

  public Html setKeepFont(boolean keepFont) {
    myKeepFont = keepFont;
    return this;
  }

  public boolean isKeepFont() {
    return myKeepFont;
  }
}
