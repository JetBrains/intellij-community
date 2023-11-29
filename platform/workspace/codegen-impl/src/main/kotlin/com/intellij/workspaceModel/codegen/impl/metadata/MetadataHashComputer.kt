package com.intellij.workspaceModel.codegen.impl.metadata

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ValueType


internal interface MetadataHashComputer<T> {
  fun computeHash(obj: T): MetadataHash
}

internal typealias MetadataHash = Int


internal class EntityMetadataHashComputer(
  private val builtEntitiesMetadata: MutableMap<String, String>,
): MetadataHashComputer<ObjClass<*>> {
  override fun computeHash(obj: ObjClass<*>): MetadataHash {
    val metadata = builtEntitiesMetadata[obj.fullName] ?: error("Metadata for the entity ${obj.name} was not built")
    return metadata.hashCode()
  }
}

internal class ClassMetadataHashComputer(
  private val classMetadataBuilder: MetadataBuilder<ValueType.JvmClass<*>>
): MetadataHashComputer<ValueType.JvmClass<*>> {
  override fun computeHash(obj: ValueType.JvmClass<*>): MetadataHash {
    return classMetadataBuilder.buildMetadata(obj).hashCode()
  }
}