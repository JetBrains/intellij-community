// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.ir.configuration

import com.intellij.remoteServer.ir.configuration.BaseExtendableConfiguration.Companion.getTypeImpl

abstract class RemoteTargetConfiguration(typeId: String) : BaseExtendableConfiguration(typeId, RemoteTargetType.EXTENSION_NAME) {
  private val resolvedRuntimes: MutableList<LanguageRuntimeConfiguration> = mutableListOf();

}

fun <C : RemoteTargetConfiguration, T : RemoteTargetType<C>> C.getTargetType(): T = this.getTypeImpl()



