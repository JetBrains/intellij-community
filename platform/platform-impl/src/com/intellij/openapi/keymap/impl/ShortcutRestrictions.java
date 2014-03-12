/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.keymap.impl;

public class ShortcutRestrictions {
  public static final ShortcutRestrictions NO_RESTRICTIONS = new ShortcutRestrictions(true, true, true, true);

  public final boolean allowMouseShortcut;
  public final boolean allowMouseDoubleClick;
  public final boolean allowKeyboardShortcut;
  public final boolean allowAbbreviation;

  public ShortcutRestrictions(boolean allowMouseShortcut, boolean allowMouseDoubleClick, boolean allowKeyboardShortcut, boolean allowAbbreviation) {
    this.allowMouseShortcut = allowMouseShortcut;
    this.allowMouseDoubleClick = allowMouseDoubleClick;
    this.allowKeyboardShortcut = allowKeyboardShortcut;
    this.allowAbbreviation = allowAbbreviation;
  }
}
