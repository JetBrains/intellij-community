// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.jfr

import jdk.jfr.Event

internal abstract class AbstractCompletionEvent : Event() {
    abstract var wasInterrupted: Boolean
}