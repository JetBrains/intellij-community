// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package io.netty.bootstrap

import io.netty.channel.Channel
import io.netty.channel.ChannelFuture

object BootstrapUtil {
  @Throws(Throwable::class)
  fun initAndRegister(channel: Channel, bootstrap: Bootstrap): ChannelFuture {
    try {
      bootstrap.init(channel)
    }
    catch (e: Throwable) {
      channel.unsafe().closeForcibly()
      throw e
    }

    val registrationFuture = bootstrap.group().register(channel)
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
