// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("KotlinTestUtils")

package org.jetbrains.kotlin.idea.base.test

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction

fun <R> executeOnPooledThreadInReadAction(action: () -> R): R {
    val result = ApplicationManager.getApplication().executeOnPooledThread<R> { runReadAction(action) }
    return result.get()
}