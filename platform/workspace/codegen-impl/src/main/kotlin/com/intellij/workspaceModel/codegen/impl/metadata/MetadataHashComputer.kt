package com.intellij.workspaceModel.codegen.impl.metadata

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ValueType


internal interface MetadataHashComputer<T> {
  fun computeHash(obj: T): MetadataHash
}

internal typealias MetadataHash = Int


internal abstract class BaseMetadataHashComputer<T>(
  private val metadataBuilder: MetadataBuilder<T>
): MetadataHashComputer<T> {
  override fun computeHash(obj: T): MetadataHash {
    startHashComputing()
    val metadata = metadataBuilder.buildMetadata(obj)
    endHashComputing()
    return metadata.hashCode()
  }
}

internal class EntityMetadataHashComputer(
  builtPrimitiveTypes: MutableSet<BuiltPrimitiveType>
): BaseMetadataHashComputer<ObjClass<*>>(EntityMetadataBuilder(builtPrimitiveTypes))

internal class ClassMetadataHashComputer(
  builtPrimitiveTypes: MutableSet<BuiltPrimitiveType>
): BaseMetadataHashComputer<ValueType.JvmClass<*>>(ClassMetadataBuilder.newInstance(builtPrimitiveTypes))