// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.util

import com.intellij.platform.diagnostic.telemetry.Scope

@JvmField
val ActionSystem: Scope = Scope("actionSystem", verbose = true)