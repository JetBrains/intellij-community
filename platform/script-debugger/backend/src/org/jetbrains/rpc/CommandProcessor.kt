package org.jetbrains.rpc

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.jsonProtocol.Request
import java.util.concurrent.atomic.AtomicInteger

val LOG = Logger.getInstance(CommandProcessor::class.java)

abstract class CommandProcessor<INCOMING, INCOMING_WITH_SEQ : Any, SUCCESS_RESPONSE>() : CommandSenderBase<SUCCESS_RESPONSE>(), MessageManager.Handler<Request<out Any>, INCOMING, INCOMING_WITH_SEQ, SUCCESS_RESPONSE>, ResultReader<SUCCESS_RESPONSE>, MessageProcessor {
  private val currentSequence = AtomicInteger()
  protected val messageManager = MessageManager(this)

  override fun cancelWaitingRequests() {
    messageManager.cancelWaitingRequests()
  }

  override fun closed() {
    messageManager.closed()
  }

  override fun getUpdatedSequence(message: Request<out Any>): Int {
    val id = currentSequence.incrementAndGet()
    message.finalize(id)
    return id
  }

  override final fun <RESULT : Any> doSend(message: Request<RESULT>, callback: CommandSenderBase.RequestPromise<SUCCESS_RESPONSE, RESULT>) {
    messageManager.send(message, callback)
  }
}