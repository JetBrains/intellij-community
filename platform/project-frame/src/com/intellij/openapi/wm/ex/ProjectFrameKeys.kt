// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex

import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Normalizes an extension-declared project-frame key (a frame type id, a place, an action id):
 * surrounding whitespace is insignificant and a blank value means "absent".
 */
@Internal
fun String?.normalizeProjectFrameKey(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
