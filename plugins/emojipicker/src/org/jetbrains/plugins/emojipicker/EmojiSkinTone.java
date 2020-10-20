// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.emojipicker;

import org.jetbrains.annotations.NonNls;

public enum EmojiSkinTone {
  NO_TONE(""),
  LIGHT("🏻"),
  MEDIUM_LIGHT("🏼"),
  MEDIUM("🏽"),
  MEDIUM_DARK("🏾"),
  DARK("🏿");

  @NonNls private final String myValue;

  EmojiSkinTone(@NonNls String value) {this.myValue = value;}

  @NonNls
  public String getStringValue() {
    return myValue;
  }
}
