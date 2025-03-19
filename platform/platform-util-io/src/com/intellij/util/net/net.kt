// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import org.jetbrains.annotations.ApiStatus
import java.net.InetAddress
import java.net.InetSocketAddress

@ApiStatus.Internal
@JvmOverloads
fun loopbackSocketAddress(port: Int = -1): InetSocketAddress = InetSocketAddress(InetAddress.getLoopbackAddress(), if (port == -1) NetUtils.findAvailableSocketPort() else port)