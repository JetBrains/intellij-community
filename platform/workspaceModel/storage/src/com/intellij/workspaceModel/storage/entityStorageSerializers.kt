// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import java.io.InputStream
import java.io.OutputStream

interface EntityStorageSerializer {
  val serializerDataFormatVersion: String

  fun serializeCache(stream: OutputStream, storage: WorkspaceEntityStorage)
  fun deserializeCache(stream: InputStream): WorkspaceEntityStorageBuilder?
}

interface EntityTypesResolver {
  fun getPluginId(clazz: Class<*>): String?
  fun resolveClass(name: String, pluginId: String?): Class<*>
}
