// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package io.netty.bootstrap

import io.netty.channel.Channel
import io.netty.channel.ChannelFuture

object BootstrapUtil {
  fun initAndRegister(channel: Channel, bootstrap: Bootstrap): ChannelFuture {
    try {
      bootstrap.init(channel)
    }
    catch (e: Throwable) {
      channel.unsafe().closeForcibly()
      throw e
    }

    val registrationFuture = bootstrap.config().group().register(channel)
    if (registrationFuture.cause() != null) {
      if (channel.isRegistered) {
        channel.close()
      }
      else {
        channel.unsafe().closeForcibly()
      }
    }
    return registrationFuture
  }
}
