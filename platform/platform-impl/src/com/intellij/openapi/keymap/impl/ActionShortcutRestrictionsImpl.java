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

import com.intellij.openapi.actionSystem.IdeActions;
import org.jetbrains.annotations.NotNull;

public class ActionShortcutRestrictionsImpl extends ActionShortcutRestrictions {
  private static final ShortcutRestrictions MOUSE_SINGLE_CLICK_ONLY = new ShortcutRestrictions(true, true, false, false, false);
  private static final ShortcutRestrictions FIXED_SHORTCUT = new ShortcutRestrictions(false, false, false, false, false);

  @Override
  @NotNull
  public ShortcutRestrictions getForActionId(String actionId) {
    if (IdeActions.ACTION_EDITOR_ADD_OR_REMOVE_CARET.equals(actionId) ||
        IdeActions.ACTION_EDITOR_CREATE_RECTANGULAR_SELECTION.equals(actionId) ||
        IdeActions.ACTION_EDITOR_ADD_RECTANGULAR_SELECTION_ON_MOUSE_DRAG.equals(actionId)) {
      return MOUSE_SINGLE_CLICK_ONLY;
    }
    if (IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB.equals(actionId)) {
      return FIXED_SHORTCUT;
    }
    return ShortcutRestrictions.NO_RESTRICTIONS;
  }
}
