// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.io.addChannelListener
import com.intellij.util.io.shutdownIfOio
import io.netty.channel.Channel
import io.netty.util.ReferenceCountUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.errorIfNotMessage
import org.jetbrains.concurrency.nullPromise
import org.jetbrains.jsonProtocol.Request
import org.jetbrains.rpc.CONNECTION_CLOSED_MESSAGE
import org.jetbrains.rpc.MessageProcessor

@ApiStatus.Internal
open class StandaloneVmHelper(private val vm: Vm, private val messageProcessor: MessageProcessor, channel: Channel) : AttachStateManager {
  @Volatile
  private var channel: Channel? = channel

  fun getChannelIfActive(): Channel? {
    val currentChannel = channel
    return if (currentChannel == null || !currentChannel.isActive) null else currentChannel
  }

  fun write(content: Any): Boolean {
    val channel = getChannelIfActive()
    if (channel != null) {
      return !channel.writeAndFlush(content).isCancelled
    } else {
      ReferenceCountUtil.release(content)
      return false
    }
  }

  interface VmEx : Vm {
    fun createDisconnectRequest(): Request<out Any>?
  }

  override val isAttached: Boolean
    get() = channel != null

  override fun detach(): Promise<*> {
    val currentChannel = channel ?: return nullPromise()

    messageProcessor.cancelWaitingRequests()
    val disconnectRequest = (vm as? VmEx)?.createDisconnectRequest()
    val promise = AsyncPromise<Any?>()
    if (disconnectRequest == null) {
      messageProcessor.closed()
      channel = null
    }
    else {
      messageProcessor.send(disconnectRequest)
        .onError {
          if (it.message != CONNECTION_CLOSED_MESSAGE) {
            thisLogger().errorIfNotMessage(it)
          }
        }
      // we don't wait response because 1) no response to "disconnect" message (V8 for example) 2) closed message manager just ignore any incoming messages
      currentChannel.flush()
      messageProcessor.closed()
      channel = null
      messageProcessor.cancelWaitingRequests()
    }
    closeChannel(currentChannel, promise)
    return promise
  }

  protected open fun closeChannel(channel: Channel, promise: AsyncPromise<Any?>) {
    doCloseChannel(channel, promise)
  }
}

@ApiStatus.Internal
fun doCloseChannel(channel: Channel, promise: AsyncPromise<Any?>) {
  channel.close().addChannelListener {
    try {
      it.channel().eventLoop().shutdownIfOio()
    }
    finally {
      val error = it.cause()
      if (error == null) {
        promise.setResult(null)
      }
      else {
        promise.setError(error)
      }
    }
  }
}