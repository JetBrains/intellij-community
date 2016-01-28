/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import io.netty.channel.Channel
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import org.jetbrains.io.addChannelListener
import org.jetbrains.io.shutdownIfOio
import org.jetbrains.jsonProtocol.Request
import org.jetbrains.rpc.CONNECTION_CLOSED_MESSAGE
import org.jetbrains.rpc.LOG
import org.jetbrains.rpc.MessageProcessor
import org.jetbrains.concurrency.Promise as OJCPromise

open class StandaloneVmHelper(private val vm: Vm, private val messageProcessor: MessageProcessor, channel: Channel) : AttachStateManager {
  private @Volatile var channel: Channel? = channel

  init {
    channel.closeFuture().addChannelListener {
      // don't report in case of explicit detach()
      if (this.channel != null) {
        messageProcessor.closed()
        vm.debugListener.disconnected()
      }
    }
  }

  fun getChannelIfActive(): Channel? {
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

  override val isAttached: Boolean
    get() = channel != null

  override fun detach(): Promise<*> {
    val currentChannel = channel ?: return resolvedPromise()

    messageProcessor.cancelWaitingRequests()
    val disconnectRequest = (vm as? VmEx)?.createDisconnectRequest()
    val promise = AsyncPromise<Any?>()
    if (disconnectRequest == null) {
      messageProcessor.closed()
      channel = null
    }
    else {
      messageProcessor.send(disconnectRequest)
        .rejected {
          if (it.message != CONNECTION_CLOSED_MESSAGE) {
            LOG.error(it)
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