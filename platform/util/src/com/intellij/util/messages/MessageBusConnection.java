/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.util.messages;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Aggregates multiple topic subscriptions for particular {@link MessageBus message bus}. I.e. every time a client wants to
 * listen for messages it should grab appropriate connection (or create a new one) and {@link #subscribe(Topic, Object) subscribe}
 * to particular endpoint.
 */
public interface MessageBusConnection extends Disposable {

  /**
   * Subscribes given handler to the target endpoint within the current connection.
   *
   * @param topic    target endpoint
   * @param handler  target handler to use for incoming messages
   * @param <L>      interface for working with the target topic
   * @throws IllegalStateException    if there is already registered handler for the target endpoint within the current connection.
   *                                  Note that that previously registered handler is not replaced by the given one then
   * @see MessageBus#syncPublisher(Topic)
   */
  <L> void subscribe(@NotNull Topic<L> topic, @NotNull L handler) throws IllegalStateException;

  /**
   * Subscribes to the target topic within the current connection using {@link #setDefaultHandler(MessageHandler) default handler}.
   *
   * @param topic  target endpoint
   * @param <L>    interface for working with the target topic
   * @throws IllegalStateException    if {@link #setDefaultHandler(MessageHandler) default handler} hasn't been defined or
   *                                  has incompatible type with the {@link Topic#getListenerClass() topic's business interface}
   *                                  or if target topic is already subscribed within the current connection
   */
  <L> void subscribe(@NotNull Topic<L> topic) throws IllegalStateException;

  /**
   * Allows to specify default handler to use during {@link #subscribe(Topic) anonymous subscriptions}.
   *
   * @param handler  handler to use
   */
  void setDefaultHandler(@Nullable MessageHandler handler);

  /**
   * Forces to process any queued but not delivered events.
   *
   * @see MessageBus#syncPublisher(Topic)
   */
  void deliverImmediately();

  /**
   * Disconnects current connections from the {@link MessageBus message bus} and drops all queued but not dispatched messages (if any)
   */
  void disconnect();
}
