/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.messages.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author yole
 */
public class MessageListenerList<T> {
  private final MessageBus myMessageBus;
  private final Topic<T> myTopic;
  private final Map<T, MessageBusConnection> myListenerToConnectionMap = ContainerUtil.newConcurrentMap();

  public MessageListenerList(@NotNull MessageBus messageBus, @NotNull Topic<T> topic) {
    myTopic = topic;
    myMessageBus = messageBus;
  }

  public void add(@NotNull T listener) {
    final MessageBusConnection connection = myMessageBus.connect();
    connection.subscribe(myTopic, listener);
    myListenerToConnectionMap.put(listener, connection);
  }

  public void add(@NotNull final T listener, @NotNull Disposable parentDisposable) {
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        myListenerToConnectionMap.remove(listener);
      }
    });
    final MessageBusConnection connection = myMessageBus.connect(parentDisposable);
    connection.subscribe(myTopic, listener);
    myListenerToConnectionMap.put(listener, connection);
  }

  public void remove(@NotNull T listener) {
    final MessageBusConnection connection = myListenerToConnectionMap.remove(listener);
    if (connection != null) {
      connection.disconnect();
    }
  }
}
