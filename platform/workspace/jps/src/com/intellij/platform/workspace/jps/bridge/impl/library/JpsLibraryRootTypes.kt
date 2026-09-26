// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.library

import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer

private val rootTypes by lazy(LazyThreadSafetyMode.PUBLICATION) { 
  val types = HashMap<String, JpsOrderRootType>()
  JpsLibraryTableSerializer.PREDEFINED_ROOT_TYPES_SERIALIZERS.associateByTo(types, { it.typeId }, { it.type })
  JpsModelSerializerExtension.getExtensions().forEach { 
    it.libraryRootTypeSerializers.associateByTo(types, { it.typeId }, { it.type })
  }
  types
}

internal fun LibraryRootTypeId.asJpsOrderRootType(): JpsOrderRootType = rootTypes[name] ?: JpsOrderRootType.COMPILED