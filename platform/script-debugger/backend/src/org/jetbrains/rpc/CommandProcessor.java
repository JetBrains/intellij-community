package org.jetbrains.rpc;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jsonProtocol.Request;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class CommandProcessor<INCOMING, INCOMING_WITH_SEQ, SUCCESS_RESPONSE>
  extends CommandSenderBase<SUCCESS_RESPONSE>
  implements MessageManager.Handler<Request, INCOMING, INCOMING_WITH_SEQ, SUCCESS_RESPONSE>,
             ResultReader<SUCCESS_RESPONSE>,
             MessageProcessor {
  public static final Logger LOG = Logger.getInstance(CommandProcessor.class);

  private final AtomicInteger currentSequence = new AtomicInteger();
  protected final MessageManager<Request, INCOMING, INCOMING_WITH_SEQ, SUCCESS_RESPONSE> messageManager;

  protected CommandProcessor() {
    messageManager = new MessageManager<Request, INCOMING, INCOMING_WITH_SEQ, SUCCESS_RESPONSE>(this);
  }

  @Override
  public final void cancelWaitingRequests() {
    messageManager.cancelWaitingRequests();
  }

  @Override
  public final void closed() {
    messageManager.closed();
  }

  @Override
  public final int getUpdatedSequence(@NotNull Request message) {
    int id = currentSequence.incrementAndGet();
    message.finalize(id);
    return id;
  }

  @Override
  protected <RESULT> void send(@NotNull Request message, @NotNull RequestPromise<SUCCESS_RESPONSE, RESULT> callback) {
    messageManager.send(message, callback);
  }
}