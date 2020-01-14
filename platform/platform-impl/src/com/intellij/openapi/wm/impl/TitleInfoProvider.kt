// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface TitleInfoProvider {
  companion object {
    var EP: ExtensionPointName<TitleInfoProvider> = ExtensionPointName.create("com.intellij.titleInfoProvider")

    @JvmStatic
    fun getProviders(project: Project, listener: (provider: TitleInfoProvider) -> Unit): List<TitleInfoProvider> {
      val list = EP.getExtensionList(project)
      list.forEach{it.addUpdateListener(listener)}

      return list
    }

    @JvmStatic
    fun getProviders(project: Project): List<TitleInfoProvider> {
      return EP.getExtensionList(project)
    }
  }

  fun addUpdateListener(value: (provider: TitleInfoProvider) -> Unit) {
    addUpdateListener(null, value)
  }

  fun addUpdateListener(disposable: Disposable?, value: (provider: TitleInfoProvider) -> Unit)
  val isActive: Boolean
  val value: String

  val borderlessSuffix: String
  val borderlessPrefix: String

}