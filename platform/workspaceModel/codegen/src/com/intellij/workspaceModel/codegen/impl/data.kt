package org.jetbrains.deft.bytes

import com.google.common.primitives.Longs
import kotlinx.io.core.Input
import kotlinx.io.core.Output

const val useCodegenImpl: Boolean = true

val logUpdates: Boolean = System.getProperty("deft.trace.updates") != null
const val objDebugMarkers: Boolean = true
const val objDebugCheckLoaded: Boolean = true
val objStartDebugMarker: Long = Longs.fromByteArray("objStart".toByteArray())
val objEndDebugMarker: Long = Longs.fromByteArray("objEnd__".toByteArray())