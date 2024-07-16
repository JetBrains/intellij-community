// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.replaceWith

data class ReplaceWithData(val pattern: String, val imports: List<String>, val replaceInWholeProject: Boolean)