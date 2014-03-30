/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ui.mac;

import java.util.LinkedList;

/**
 * @author Denis Fokin
 */
class MacMessagesQueue<T extends Runnable> {

  private boolean waitingForAppKit = false;
  private LinkedList<Runnable> queueModel = new LinkedList<Runnable>();

  synchronized void runOrEnqueue (final T runnable) {
    if (waitingForAppKit) {
      enqueue(runnable);
    } else {
      runnable.run();
      waitingForAppKit = true;
    }
  }

  private void enqueue (final T runnable) {
    queueModel.add(runnable);
  }

  synchronized void runFromQueue () {
    if (!queueModel.isEmpty()) {
      queueModel.remove().run();
      waitingForAppKit = true;
    } else {
      waitingForAppKit = false;
    }
  }
}
