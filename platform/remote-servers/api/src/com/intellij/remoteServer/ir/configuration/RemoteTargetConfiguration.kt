// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.ir.configuration

abstract class RemoteTargetConfiguration(val typeId: String) {
  var displayName: String = ""
}

@Suppress("UNCHECKED_CAST")
fun <C : RemoteTargetConfiguration> C.getTargetType(): RemoteTargetType<C> =
  RemoteTargetType.findTargetType(this.typeId) as RemoteTargetType<C>

