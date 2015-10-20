/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.debugger

import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.oio.OioEventLoopGroup
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.catchError
import org.jetbrains.concurrency.resolvedPromise
import org.jetbrains.jsonProtocol.Request
import org.jetbrains.rpc.MessageProcessor
import org.jetbrains.rpc.MessageWriter
import java.util.concurrent.TimeUnit
import org.jetbrains.concurrency.Promise as OJCPromise

open class StandaloneVmHelper(private val vm: Vm, private val messageProcessor: MessageProcessor) : MessageWriter(), AttachStateManager {
  private @Volatile var channel: Channel? = null

  override fun write(content: ByteBuf) = write((content as Any))

  public fun getChannelIfActive(): Channel? {
    val currentChannel = channel
    return if (currentChannel == null || !currentChannel.isActive) null else currentChannel
  }

  fun write(content: Any): Boolean {
    val channel = getChannelIfActive()
    return channel != null && !channel.writeAndFlush(content).isCancelled
  }

  interface VmEx : Vm {
    fun createDisconnectRequest(): Request<out Any>?
  }

  fun setChannel(channel: Channel) {
    this.channel = channel
    channel.closeFuture().addListener(MyChannelFutureListener())
  }

  private inner class MyChannelFutureListener : ChannelFutureListener {
    override fun operationComplete(future: ChannelFuture) {
      // don't report in case of explicit detach()
      if (channel != null) {
        messageProcessor.closed()
        vm.debugListener.disconnected()
      }
    }
  }

  override fun isAttached() = channel != null

  override fun detach(): Promise<*> {
    val currentChannel = channel ?: return resolvedPromise()

    messageProcessor.cancelWaitingRequests()
    val disconnectRequest = (vm as? VmEx)?.createDisconnectRequest()

    val promise = AsyncPromise<Any?>()
    if (disconnectRequest == null) {
      messageProcessor.closed()
      channel = null
      closeChannel(currentChannel, promise)
      return promise
    }

    messageProcessor.closed()
    channel = null
    val p = messageProcessor.send(disconnectRequest)
    p.processed {
      promise.catchError {
        messageProcessor.cancelWaitingRequests()
        closeChannel(currentChannel, promise)
      }
    }
    return promise
  }

  protected open fun closeChannel(channel: Channel, promise: AsyncPromise<Any?>) {
    doCloseChannel(channel, promise)
  }
}

fun doCloseChannel(channel: Channel, promise: AsyncPromise<Any?>) {
  val eventLoop = channel.eventLoop()
  channel.close().addListener(object : ChannelFutureListener {
    override fun operationComplete(future: ChannelFuture) {
      try {
        // if NIO, so, it is shared and we don't need to release it
        if (eventLoop is OioEventLoopGroup) {
          @Suppress("USELESS_CAST")
          (eventLoop as OioEventLoopGroup).shutdownGracefully(1L, 2L, TimeUnit.NANOSECONDS)
        }
      }
      finally {
        val error = future.cause()
        if (error == null) {
          promise.setResult(null)
        }
        else {
          promise.setError(error)
        }
      }
    }
  })
}