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
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 22, 2006
 * Time: 9:48:08 PM
 */
package com.intellij.util.messages;

import com.intellij.openapi.Disposable;

/**
 * Use ComponentManager.getMessageBus() to obtain one.
 */
public interface MessageBus {
  MessageBusConnection connect();
  MessageBusConnection connect(Disposable parentDisposable);

  <L> L syncPublisher(Topic<L> topic);
  <L> L asyncPublisher(Topic<L> topic);

  void dispose();
}