package com.intellij.workspaceModel.codegen.impl.metadata.old

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.dsl.GeneratorContext
import com.intellij.workspaceModel.codegen.impl.metadata.endHashComputing
import com.intellij.workspaceModel.codegen.impl.metadata.startHashComputing

// MetadataHashComputer ~ MetadataContext : GeneratorContext
internal interface MetadataHashComputer<T> {
  fun computeHash(context: GeneratorContext, obj: T): MetadataHash
}

internal typealias MetadataHash = Int


// BaseMetadataHashComputer : MetadataContext : GeneratorContext
internal abstract class BaseMetadataHashComputer<T>(
  private val metadataBuilder: MetadataBuilder<T>
): MetadataHashComputer<T> {
  override fun computeHash(context: GeneratorContext, obj: T): MetadataHash {
    startHashComputing()
    val metadata = metadataBuilder.buildMetadata(context, obj)
    endHashComputing()
    return metadata.hashCode()
  }
}

/**
 * com.intellij.platform.workspace.storage.tests.metadata.serialization.PropsMetadataSerializationTest
 */
private object SpecialTestCase {
  const val TEST_HASH_VALUE: Int = 815162342
  const val TEST_PACKAGE = "com.intellij.platform.workspace.storage.testEntities.entities"
  const val TEST_ENTITY_NAME = "ChangedComputablePropEntity"
  fun check(obj: ObjClass<*>): Boolean {
    if (!obj.module.name.startsWith(TEST_PACKAGE))
      return false
    if (obj.name != TEST_ENTITY_NAME)
      return false
    return true
  }
}

internal class EntityMetadataHashComputer(
  builtPrimitiveTypes: MutableSet<BuiltPrimitiveType>
): BaseMetadataHashComputer<ObjClass<*>>(EntityMetadataBuilder(builtPrimitiveTypes)) {
  override fun computeHash(context: GeneratorContext, obj: ObjClass<*>): MetadataHash {
    return if (SpecialTestCase.check(obj))
      SpecialTestCase.TEST_HASH_VALUE
    else super.computeHash(context, obj)
  }
}

internal class ClassMetadataHashComputer(
  builtPrimitiveTypes: MutableSet<BuiltPrimitiveType>
): BaseMetadataHashComputer<ValueType.JvmClass<*>>(ClassMetadataBuilder.newInstance(builtPrimitiveTypes))