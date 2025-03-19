// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.actions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface OpenProjectMethodProvider {
  fun openProjectInHeadlessMode(projectPath: String): Project

  companion object {
    private val EP_NAME = ExtensionPointName.create<OpenProjectMethodProvider>("com.intellij.cce.openProjectMethodProvider")

    fun find(): OpenProjectMethodProvider? {
      return EP_NAME.extensionList.singleOrNull()
    }
  }
}