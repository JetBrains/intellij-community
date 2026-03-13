// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system

import org.jetbrains.annotations.ApiStatus

@MustBeDocumented
@Retention(value = AnnotationRetention.BINARY)
@RequiresOptIn(
  level = RequiresOptIn.Level.WARNING,
  message = "Please, read `LowLevelLocalMachineAccess` annotation doc thoroughly, to make sure you know what you are doing"
)
/**
 * In most cases this is *not* what you are looking for.
 * Except for the lowest level, all code must be written against Eel: [com.intellij.platform.eel.EelApi].
 * You should either get [com.intellij.platform.eel.EelApi] as an argument, or obtain it from [java.nio.file.Path] or project,
 * and use [com.intellij.platform.eel.EelApi.platform] to check an OS.
 */
@ApiStatus.Internal
annotation class LowLevelLocalMachineAccess
