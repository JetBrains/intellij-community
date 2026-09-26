// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.tracing

import com.intellij.platform.diagnostic.telemetry.Scope

@JvmField
val RDCT: Scope = Scope("rdct")

@JvmField
val Connection: Scope = Scope("connection", RDCT)

@JvmField
val Lux = Scope("lux", RDCT)
