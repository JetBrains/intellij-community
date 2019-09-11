// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.ir.configuration

abstract class RemoteTargetConfiguration(typeId: String) : BaseExtendableConfiguration(typeId)

fun <C : RemoteTargetConfiguration> C.getTargetType(): RemoteTargetType<C> =
  this.getExtendableTypeImpl(RemoteTargetType.EXTENSION_NAME)


