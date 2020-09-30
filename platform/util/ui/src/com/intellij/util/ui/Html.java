// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import org.jetbrains.annotations.Nls;

public class Html {

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
