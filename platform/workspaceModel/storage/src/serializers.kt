// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api

import java.io.InputStream
import java.io.OutputStream

interface EntityStorageSerializer {
  val serializerDataFormatVersion: String

  fun serializeCache(stream: OutputStream, storage: TypedEntityStorage)
  fun deserializeCache(stream: InputStream): TypedEntityStorageBuilder
}

interface EntityTypesResolver {
  fun getPluginId(clazz: Class<*>): String?
  fun resolveClass(name: String, pluginId: String?): Class<*>
}
