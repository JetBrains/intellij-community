// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.BazelEnvironmentUtil
import com.intellij.util.system.OS
import org.jetbrains.annotations.ApiStatus
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * Default local inet address to listen on or connect to.
 *
 * It's usually `127.0.0.1`, but you should not depend on it due to various
 * cases handled by this function.
 *
 * Use localhostInetAddress().hostAddress to get a string representation.
 */
@ApiStatus.Internal
fun localhostInetAddress(): InetAddress = LoopbackUtils.localhostInetAddress

@ApiStatus.Internal
@JvmOverloads
fun loopbackSocketAddress(port: Int = -1): InetSocketAddress {
  return InetSocketAddress(localhostInetAddress(),
                           if (port == -1) NetUtils.findAvailableSocketPort() else port)
}

private object LoopbackUtils {
  val localhostInetAddress: InetAddress by lazy {
    // Special workaround under Bazel hermetic sandbox and macOS
    // https://github.com/bazelbuild/bazel/issues/5206#issuecomment-402398624
    if (OS.CURRENT == OS.macOS && BazelEnvironmentUtil.isBazelTestRun()) {
      Logger.getInstance(javaClass).warn("returning '::1' as localhost listening address for Bazel hermetic sandbox due to https://github.com/bazelbuild/bazel/issues/5206#issuecomment-402398624")
      // return ::1
      return@lazy InetAddress.getByAddress("localhost",
                                           byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                                                       0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01))
    }

    return@lazy InetAddress.getLoopbackAddress()
  }
}
