// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.msg

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.jlanguage.LangTool
import com.intellij.grazie.spellcheck.GrazieSpellcheckerLifecycle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.messages.Topic

private val topic = Topic(GrazieStateLifecycle::class.java, Topic.BroadcastDirection.NONE)

interface GrazieStateLifecycle {
  /** Update Grazie state */
  fun update(prevState: GrazieConfig.State, newState: GrazieConfig.State) {}
}

@Service
class GrazieInitializerManager {
  val publisher: GrazieStateLifecycle
    get() = ApplicationManager.getApplication().messageBus.syncPublisher(topic)

  init {
    val application = ApplicationManager.getApplication()
    val connection = application.messageBus.connect()
    connection.subscribe(topic, LangTool)
    connection.subscribe(topic, application.service<GrazieSpellcheckerLifecycle>())
  }

  fun register(subscriber: GrazieStateLifecycle): MessageBusConnection {
    val connection = ApplicationManager.getApplication().messageBus.connect()
    connection.subscribe(topic, subscriber)
    return connection
  }
}
