// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("WinBuildNumber")
package com.intellij.openapi.util

import com.intellij.util.system.NativeAccess

private val cachedWinBuildNumber: Long? by lazy { NativeAccess.getInstance().windowsBuildNumber }

/** The Windows build number from the registry, or `null` when it is unknown. Read once. */
internal fun getWinBuildNumber(): Long? = cachedWinBuildNumber
