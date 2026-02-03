// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.actions.internal.resolutionDebugging

internal fun Any.instanceString(): String {
    return this::class.java.simpleName + "@" + this.hashCode().toString(16)
}
