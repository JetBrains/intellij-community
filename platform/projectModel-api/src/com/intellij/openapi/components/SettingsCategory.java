// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components;

/**
 * Being applied to the {@link State#category() State} annotation of a {@link PersistentStateComponent},
 * a SettingsCategory defines which category the group of settings belongs to. It is used by the Settings Sync plugin.
 * <p/>
 * To be synced via Settings Sync, a PersistentStateComponent must have a SettingsCategory not equal to `OTHER`.
 *
 * @see RoamingType
 */
public enum SettingsCategory {
  UI,
  KEYMAP,
  CODE,
  TOOLS,
  SYSTEM,
  PLUGINS,
  OTHER
}
