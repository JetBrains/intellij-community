// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/**
 * Provides Settings 2.0, the layered settings API, as a prototype. Settings are addressed by typed
 * {@link com.intellij.platform.settings.v2.SettingsKey}s, read and written against a
 * {@link com.intellij.platform.settings.v2.SettingsContext}, and resolved through a stack of layers.
 *
 * <p>The whole surface is {@link org.jetbrains.annotations.ApiStatus.Experimental experimental} and
 * subject to change.
 */
@ApiStatus.Experimental
package com.intellij.platform.settings.v2;

import org.jetbrains.annotations.ApiStatus;
