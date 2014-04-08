package org.jetbrains.rpc;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.StripedLockIntObjectConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;

/**
 * @param <OUTGOING> type of outgoing message
 * @param <INCOMING> type of incoming message
 * @param <INCOMING_WITH_SEQ> type of incoming message that is a command (has sequence number)
 */
public final class MessageManager<OUTGOING, INCOMING, INCOMING_WITH_SEQ, SUCCESS, ERROR_DETAILS> {
  public static final Logger LOG = Logger.getInstance(MessageManager.class);

  private final ConcurrentIntObjectMap<AsyncResultCallback<SUCCESS, ERROR_DETAILS>> callbackMap = new StripedLockIntObjectConcurrentHashMap<AsyncResultCallback<SUCCESS, ERROR_DETAILS>>();
  private final Handler<OUTGOING, INCOMING, INCOMING_WITH_SEQ, SUCCESS, ERROR_DETAILS> handler;

  private volatile boolean closed;

  public MessageManager(Handler<OUTGOING, INCOMING, INCOMING_WITH_SEQ, SUCCESS, ERROR_DETAILS> handler) {
    this.handler = handler;
  }

  public interface Handler<OUTGOING, INCOMING, INCOMING_WITH_SEQ, SUCCESS, ERROR_DETAILS> {
    int getUpdatedSequence(OUTGOING message);

    boolean write(@NotNull OUTGOING message) throws IOException;

    INCOMING_WITH_SEQ readIfHasSequence(INCOMING incoming);

    int getSequence(INCOMING_WITH_SEQ incomingWithSeq);

    void acceptNonSequence(INCOMING incoming);

    void call(INCOMING_WITH_SEQ response, AsyncResultCallback<SUCCESS, ERROR_DETAILS> callback);
  }

  public void send(@NotNull OUTGOING message, @NotNull AsyncResultCallback<SUCCESS, ERROR_DETAILS> callback) {
    if (closed) {
      callback.onError("Connection is closed", null);
      return;
    }

    int sequence = handler.getUpdatedSequence(message);
    callbackMap.put(sequence, callback);
    doSend(message, sequence);
  }

  private void doSend(@NotNull OUTGOING message, int sequence) {
    boolean success;
    try {
      success = handler.write(message);
    }
    catch (Throwable e) {
      try {
        failedToSend(sequence);
      }
      finally {
        LOG.error("Failed to send", e);
      }
      return;
    }

    if (!success) {
      failedToSend(sequence);
    }
  }

  private void failedToSend(int sequence) {
    AsyncResultCallback<SUCCESS, ERROR_DETAILS> callback = callbackMap.remove(sequence);
    if (callback != null) {
      callback.onError("Failed to send", null);
    }
  }

  public void processIncoming(INCOMING incomingParsed) {
    INCOMING_WITH_SEQ commandResponse = handler.readIfHasSequence(incomingParsed);
    if (commandResponse == null) {
      if (closed) {
        // just ignore
        LOG.info("Connection closed, ignore incoming");
      }
      else {
        handler.acceptNonSequence(incomingParsed);
      }
      return;
    }

    AsyncResultCallback<SUCCESS, ERROR_DETAILS> callback = getCallbackAndRemove(handler.getSequence(commandResponse));
    try {
      if (closed) {
        callback.onError("Connection closed", null);
      }
      else {
        handler.call(commandResponse, callback);
      }
    }
    catch (Throwable e) {
      callback.onError("Failed to dispatch response to callback", null);
      LOG.error("Failed to dispatch response to callback", e);
    }
  }

  public AsyncResultCallback<SUCCESS, ERROR_DETAILS> getCallbackAndRemove(int id) {
    AsyncResultCallback<SUCCESS, ERROR_DETAILS> callback = callbackMap.remove(id);
    if (callback == null) {
      throw new IllegalArgumentException("Cannot find callback with id " + id);
    }
    return callback;
  }

  public void closed() {
    closed = true;
  }

  public void cancelWaitingRequests() {
    // we should call them in the order they have been submitted
    ConcurrentIntObjectMap<AsyncResultCallback<SUCCESS, ERROR_DETAILS>> map = callbackMap;
    int[] keys = map.keys();
    Arrays.sort(keys);
    for (int key : keys) {
      try {
        map.get(key).onError("Connection closed", null);
      }
      catch (Throwable e) {
        LOG.error("Failed to reject callback on connection closed", e);
      }
    }
  }
}