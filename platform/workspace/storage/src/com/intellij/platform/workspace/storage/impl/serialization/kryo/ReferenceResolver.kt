// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.serialization.kryo

import com.esotericsoftware.kryo.kryo5.ReferenceResolver
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata


/**
 * Used to speed up [ReferenceResolver.useReferences] method during deserialization:
 * * It checks if metadata of that type exists, in this case it uses metadata to determine if this type is an enum or not
 * * If there is no metadata of this type, then it calculates result in the delegate and caches it
 */
internal class CachedReferenceResolver(private val referenceResolver: ReferenceResolver,
                                       metadata: List<StorageTypeMetadata>) : ReferenceResolver by referenceResolver {
  private val cachedTypes: MutableMap<String, Boolean> = hashMapOf()

  private val metadataByFqn = metadata.associate {
    if (it is EntityMetadata) {
      it.entityDataFqName to it
    }
    else {
      it.fqName to it
    }
  }

  override fun useReferences(type: Class<*>): Boolean {
    val fqn = type.name
    val typeMetadata = metadataByFqn[fqn]
    if (typeMetadata != null) {
      return typeMetadata !is FinalClassMetadata.EnumClassMetadata
    }
    return cachedTypes.getOrPut(fqn) { referenceResolver.useReferences(type) }
  }
}