// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.DefaultPartTitle

interface TitleInfoProvider {
  companion object {
    var EP: ExtensionPointName<TitleInfoProvider> = ExtensionPointName.create("com.intellij.titleInfoProvider")

    @JvmStatic
    fun getProviders(project: Project, listener: () -> Unit): List<TitleInfoProvider> {
      val list = EP.getExtensionList(project)
      list.forEach{it.addUpdateListener(listener)}

      return list
    }
  }

  fun addUpdateListener(value: () -> Unit) {
    addUpdateListener(value, null)
  }

  fun addUpdateListener(value: () -> Unit, disposable: Disposable?)
  val borderlessTitlePart: DefaultPartTitle
  val isActive: Boolean
  val value: String
}