// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.awt.Color

// TODO: move to RPC module!!
// TODO[IJPL-160146]: Implement implement Color serialization
@Serializable
data class XValueMarkerDto(val text: String, @Transient val color: Color? = null, val tooltipText: String?)