package com.intellij.remoteDev.tracing

import com.intellij.diagnostic.telemetry.Scope

@JvmField
val RDCT = Scope("rdct")

@JvmField
val CONNECTION = Scope("connection", RDCT)

@JvmField
val LUX = Scope("lux", RDCT)
