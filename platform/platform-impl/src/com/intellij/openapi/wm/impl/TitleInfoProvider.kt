// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import java.util.function.Consumer

interface TitleInfoProvider {
  companion object {
    private val EP = ExtensionPointName<TitleInfoProvider>("com.intellij.titleInfoProvider")

    @JvmStatic
    fun getProviders(project: Project, listener: Consumer<TitleInfoProvider>): List<TitleInfoProvider> {
      val list = EP.extensionList
      for (it in list) {
        it.addUpdateListener(project) { listener.accept(it) }
      }
      return list
    }

    @JvmStatic
    fun getProviders(): List<TitleInfoProvider> = EP.extensionList
  }

  fun isActive(project: Project): Boolean

  fun getValue(project: Project): String

  val borderlessSuffix: String
  val borderlessPrefix: String

  fun addUpdateListener(project: Project, value: (provider: TitleInfoProvider) -> Unit)
}