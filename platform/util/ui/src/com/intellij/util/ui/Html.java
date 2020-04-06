// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

public class Html {

  private final String myText;
  private boolean myKeepFont = false;
  private boolean myEagerWrap;

  public Html(String text) {
    myText = text;
  }

  public String getText() {
    return myText;
  }

  public Html setKeepFont(boolean keepFont) {
    myKeepFont = keepFont;
    return this;
  }

  public boolean isKeepFont() {
    return myKeepFont;
  }

  /**
   * By default, text can be soft-wrapped only at space positions. 'Smart' wrapping enables wrapping also at other places (e.g. punctuation)
   */
  public Html setEagerWrap(boolean eagerWrap) {
    myEagerWrap = eagerWrap;
    return this;
  }

  public boolean isEagerWrap() {
    return myEagerWrap;
  }
}
