// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

interface TitleInfoProvider {
  @ApiStatus.Internal
  interface TitleInfoProviderListener {
    fun configurationChanged()
  }

  companion object {
    @Topic.AppLevel
    @ApiStatus.Internal
    @JvmField
    val TOPIC = Topic(TitleInfoProviderListener::class.java, Topic.BroadcastDirection.NONE)

    @ApiStatus.Internal
    @JvmField
    val EP = ExtensionPointName<TitleInfoProvider>("com.intellij.titleInfoProvider")

    fun getProviders(): List<TitleInfoProvider> = EP.extensionList

    fun fireConfigurationChanged() {
      ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).configurationChanged()
    }
  }

  fun isActive(project: Project): Boolean

  fun getValue(project: Project): String

  val borderlessSuffix: String
  val borderlessPrefix: String

  fun addUpdateListener(project: Project, disp: Disposable, value: (provider: TitleInfoProvider) -> Unit)
}