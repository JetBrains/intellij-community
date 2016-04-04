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
package com.intellij.openapi.application

import com.intellij.openapi.components.impl.stores.BatchUpdateListener
import com.intellij.util.messages.MessageBus
import javax.swing.SwingUtilities

inline fun <T> runWriteAction(runnable: () -> T): T {
  val token = WriteAction.start()
  try {
    return runnable()
  }
  finally {
    token.finish()
  }
}

inline fun <T> runReadAction(runnable: () -> T): T {
  val token = ReadAction.start()
  try {
    return runnable()
  }
  finally {
    token.finish()
  }
}

inline fun <T> runBatchUpdate(messageBus: MessageBus, runnable: () -> T): T {
  val publisher = messageBus.syncPublisher(BatchUpdateListener.TOPIC)
  publisher.onBatchUpdateStarted()
  try {
    return runnable()
  }
  finally {
    publisher.onBatchUpdateFinished()
  }
}

/**
 * @exclude Internal use only
 */
fun invokeAndWaitIfNeed(runnable: () -> Unit) {
  val app = ApplicationManager.getApplication()
  if (app == null) {
    if (SwingUtilities.isEventDispatchThread()) runnable() else SwingUtilities.invokeAndWait(runnable)
  }
  else {
    app.invokeAndWait(runnable, ModalityState.any())
  }
}
