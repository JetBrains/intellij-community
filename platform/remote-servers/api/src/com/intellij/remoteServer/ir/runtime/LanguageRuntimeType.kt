// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.ir.runtime

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.remoteServer.ir.config.BaseExtendableType

abstract class LanguageRuntimeType<C : LanguageRuntimeConfiguration>(id: String) : BaseExtendableType<C>(id) {

  abstract fun isApplicableTo(runConfig: RunnerAndConfigurationSettings): Boolean

  companion object {
    val EXTENSION_NAME = ExtensionPointName.create<LanguageRuntimeType<*>>("com.intellij.ir.languageRuntime")
  }
}