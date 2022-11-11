// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

@file:JvmName("KotlinNameHighlightingStateUtils")

package org.jetbrains.kotlin.idea.base.highlighting

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

private val NAME_HIGHLIGHTING_STATE_KEY = Key<Boolean>("KOTLIN_NAME_HIGHLIGHTING_STATE")

@get:ApiStatus.Internal
@set:ApiStatus.Internal
var Project.isNameHighlightingEnabled: Boolean
    get() = getUserData(NAME_HIGHLIGHTING_STATE_KEY) ?: true
    internal set(value) {
        // Avoid storing garbage in the user data
        val valueToPut = if (!value) false else null
        putUserData(NAME_HIGHLIGHTING_STATE_KEY, valueToPut)
    }

@TestOnly
@ApiStatus.Internal
fun Project.withNameHighlightingDisabled(block: () -> Unit) {
    val oldValue = isNameHighlightingEnabled
    try {
        isNameHighlightingEnabled = false
        block()
    } finally {
        isNameHighlightingEnabled = oldValue
    }
}