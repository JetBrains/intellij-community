// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.msg

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.detection.LangDetector
import com.intellij.grazie.ide.inspection.detection.LanguageDetectionInspection
import com.intellij.grazie.ide.inspection.grammar.GrazieCommitInspection
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.grazie.jlanguage.LangTool
import com.intellij.grazie.spellcheck.GrazieSpellchecker
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.messages.Topic

private val topic = Topic(GrazieStateLifecycle::class.java, Topic.BroadcastDirection.NONE)

interface GrazieStateLifecycle {
  /** Initialize Grazie with passed state */
  fun init(state: GrazieConfig.State) {}

  /** Update Grazie state */
  fun update(prevState: GrazieConfig.State, newState: GrazieConfig.State) {}
}

@Service
internal class GrazieInitializerManager {
  val publisher: GrazieStateLifecycle
    get() = ApplicationManager.getApplication().messageBus.syncPublisher(topic)

  init {
    val connection = ApplicationManager.getApplication().messageBus.connect()
    connection.subscribe(topic, LangTool)
    connection.subscribe(topic, LangDetector)
    connection.subscribe(topic, GrazieSpellchecker)
    connection.subscribe(topic, GrazieCommitInspection)
    connection.subscribe(topic, GrazieInspection)
    connection.subscribe(topic, LanguageDetectionInspection)
  }

  fun register(subscriber: GrazieStateLifecycle): MessageBusConnection {
    val connection = ApplicationManager.getApplication().messageBus.connect()
    connection.subscribe(topic, subscriber)
    return connection
  }
}
