// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.ir.configuration

import com.intellij.openapi.extensions.ExtensionPointName

abstract class BaseExtendableConfiguration(val typeId: String) {
  var displayName: String = ""
}

@Suppress("UNCHECKED_CAST")
internal fun <C : BaseExtendableConfiguration, T : BaseExtendableType<C>>
  C.getExtendableTypeImpl(extPoint: ExtensionPointName<out BaseExtendableType<*>>): T {

  val result = extPoint.extensionList.find { it.id == typeId }
  return result as T?
         ?: throw IllegalStateException("for type: $typeId, name: $displayName")
}

