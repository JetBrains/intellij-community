// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl;

public final class ShortcutRestrictions {
  public static final ShortcutRestrictions NO_RESTRICTIONS = new ShortcutRestrictions(true, true, true, true, true, true);

  public final boolean allowChanging;
  public final boolean allowMouseShortcut;
  public final boolean allowMouseDoubleClick;
  public final boolean allowKeyboardShortcut;
  public final boolean allowKeyboardSecondStroke;
  public final boolean allowAbbreviation;

  public ShortcutRestrictions(boolean allowChanging,
                              boolean allowMouseShortcut,
                              boolean allowMouseDoubleClick,
                              boolean allowKeyboardShortcut,
                              boolean allowKeyboardSecondStroke,
                              boolean allowAbbreviation) {
    this.allowChanging = allowChanging;
    this.allowMouseShortcut = allowMouseShortcut;
    this.allowMouseDoubleClick = allowMouseDoubleClick;
    this.allowKeyboardShortcut = allowKeyboardShortcut;
    this.allowKeyboardSecondStroke = allowKeyboardSecondStroke;
    this.allowAbbreviation = allowAbbreviation;
  }
}
