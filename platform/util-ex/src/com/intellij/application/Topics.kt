// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("Topics")
package com.intellij.application

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.Topic

// disposable is not an optional param to force pass `null` explicitly
/**
 * Subscribes given handler to the application message bus.
 *
 * Use this shortcut method only if you need to subscribe to the one topic, otherwise you should reuse message bus connection.
 */
fun <L> Topic<L>.subscribe(disposable: Disposable?, handler: L) {
  val messageBus = ApplicationManager.getApplication().messageBus
  (if (disposable == null) messageBus.connect() else messageBus.connect(disposable)).subscribe(this, handler)
}