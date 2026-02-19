// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.ui.SimpleTextAttributes;

public final class RichTextItem {
  private final String myText;
  private final SimpleTextAttributes myTextAttributes;

  public RichTextItem(String text, SimpleTextAttributes textAttributes) {
    myText = text;
    myTextAttributes = textAttributes;
  }

  public String getText() {
    return myText;
  }

  public SimpleTextAttributes getTextAttributes() {
    return myTextAttributes;
  }
}
