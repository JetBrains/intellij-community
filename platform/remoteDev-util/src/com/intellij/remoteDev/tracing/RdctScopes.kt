package com.intellij.remoteDev.tracing

import com.intellij.platform.diagnostic.telemetry.Scope

@JvmField
val RDCT: Scope = Scope("rdct")

@JvmField
val Connection: Scope = Scope("connection", RDCT)

@JvmField
val Lux = Scope("lux", RDCT)
