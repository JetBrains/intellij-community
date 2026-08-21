// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor;

import org.jetbrains.annotations.ApiStatus;

/**
 * A configurable implementing this interface reports whether it currently holds settings that are
 * customized away from their defaults. Such configurables are painted with the "modified" (blue) mark
 * in the settings tree even when there are no unsaved changes, so customizations stay discoverable
 * after applying the settings and across dialog sessions.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
public interface CustomizedSettingsProvider {

  boolean hasCustomizedSettings();
}
