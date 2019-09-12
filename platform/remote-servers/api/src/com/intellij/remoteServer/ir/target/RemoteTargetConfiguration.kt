// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.ir.target

import com.intellij.remoteServer.ir.config.BaseExtendableConfiguration
import com.intellij.remoteServer.ir.config.BaseExtendableConfiguration.Companion.getTypeImpl
import com.intellij.remoteServer.ir.config.BaseExtendableList
import com.intellij.remoteServer.ir.runtime.LanguageRuntimeConfiguration
import com.intellij.remoteServer.ir.runtime.LanguageRuntimeType

abstract class RemoteTargetConfiguration(typeId: String)
  : BaseExtendableConfiguration(typeId, RemoteTargetType.EXTENSION_NAME) {

  internal val runtimes = BaseExtendableList(LanguageRuntimeType.EXTENSION_NAME)

  fun addLanguageRuntime(runtime: LanguageRuntimeConfiguration) = runtimes.addConfig(runtime)

  fun removeLanguageRuntime(runtime: LanguageRuntimeConfiguration) = runtimes.removeConfig(runtime)
}

fun <C : RemoteTargetConfiguration, T : RemoteTargetType<C>> C.getTargetType(): T = this.getTypeImpl()



