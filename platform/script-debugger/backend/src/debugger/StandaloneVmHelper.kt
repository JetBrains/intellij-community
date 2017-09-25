/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.util.io.addChannelListener
import com.intellij.util.io.shutdownIfOio
import io.netty.channel.Channel
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.errorIfNotMessage
import org.jetbrains.concurrency.nullPromise
import org.jetbrains.jsonProtocol.Request
import org.jetbrains.rpc.CONNECTION_CLOSED_MESSAGE
import org.jetbrains.rpc.LOG
import org.jetbrains.rpc.MessageProcessor

open class StandaloneVmHelper(private val vm: Vm, private val messageProcessor: MessageProcessor, channel: Channel) : AttachStateManager {
  private @Volatile var channel: Channel? = channel

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
        .rejected {
          if (it.message != CONNECTION_CLOSED_MESSAGE) {
            LOG.errorIfNotMessage(it)
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