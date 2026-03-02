// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.util.messages.SimpleMessageBusConnection
import org.junit.rules.ExternalResource

internal class RecentProjectManagerListenerRule : ExternalResource() {
  private var connection: SimpleMessageBusConnection? = null

  override fun before() {
    connection = ApplicationManager.getApplication().messageBus.simpleConnect()
    connection!!.subscribe(ProjectCloseListener.TOPIC, RecentProjectsManagerBase.MyProjectListener())
    connection!!.subscribe(AppLifecycleListener.TOPIC, RecentProjectsManagerBase.MyAppLifecycleListener())
  }

  override fun after() {
    connection?.disconnect()
  }
}
