// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PathManagerEx")
package com.intellij.openapi.application

import java.nio.file.Path

@Deprecated("Pointless; use `PathManager.getSystemDir()` instead", replaceWith = ReplaceWith("PathManager.getSystemDir()"), level = DeprecationLevel.ERROR)
@Suppress("unused")
val appSystemDir: Path get() = PathManager.getSystemDir()
