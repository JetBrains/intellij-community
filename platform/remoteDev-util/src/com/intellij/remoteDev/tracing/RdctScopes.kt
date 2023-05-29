package com.intellij.remoteDev.tracing

import com.intellij.platform.diagnostic.telemetry.Scope

@JvmField
val RDCT = Scope("rdct")

@JvmField
val Connection = Scope("connection", RDCT)

@JvmField
val Lux = Scope("lux", RDCT)
