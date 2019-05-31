// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.net

import java.net.InetAddress
import java.net.InetSocketAddress

@JvmOverloads
fun loopbackSocketAddress(port: Int = -1): InetSocketAddress = InetSocketAddress(InetAddress.getLoopbackAddress(), if (port == -1) NetUtils.findAvailableSocketPort() else port)