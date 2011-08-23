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
package com.intellij.util.messages.impl;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

public final class Message {
  private final Topic myTopic;
  private final Method myListenerMethod;
  private final Object[] myArgs;

  public Message(@NotNull Topic topic, @NotNull Method listenerMethod, Object[] args) {
    myTopic = topic;
    listenerMethod.setAccessible(true);
    myListenerMethod = listenerMethod;
    myArgs = args;
  }

  @NotNull
  public Topic getTopic() {
    return myTopic;
  }

  @NotNull
  public Method getListenerMethod() {
    return myListenerMethod;
  }

  public Object[] getArgs() {
    return myArgs;
  }

  public String toString() {
    return myTopic.toString() + ":" + myListenerMethod.getName();
  }
}
