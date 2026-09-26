// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.metadata.old

import com.intellij.workspaceModel.codegen.deft.meta.CompiledObjModule
import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.dsl.GeneratorContext
import com.intellij.workspaceModel.codegen.impl.dsl.generateCode
import com.intellij.workspaceModel.codegen.impl.dsl.optInWorkspaceEntityInternalApi
import com.intellij.workspaceModel.codegen.impl.dsl.packageDirective
import com.intellij.workspaceModel.codegen.impl.metadata.getFullName
import com.intellij.workspaceModel.codegen.impl.metadata.getName
import com.intellij.workspaceModel.codegen.impl.writer.MetadataStorage
import com.intellij.workspaceModel.codegen.impl.writer.StorageTypeMetadata
import com.intellij.workspaceModel.codegen.impl.writer.extensions.allFinalSubClasses
import com.intellij.workspaceModel.codegen.impl.writer.extensions.implPackage
import com.intellij.workspaceModel.codegen.impl.writer.getAllPropertiesWithOwnExtensions

internal fun GeneratorContext.metadataStorageImplCode(targetModule: CompiledObjModule, allModules: List<CompiledObjModule>): String {
  val types = allModules.flatMap { it.types }
  val abstractTypes = allModules.flatMap { it.abstractTypes }

  return generateCode {
    packageDirective(targetModule.implPackage)
    optInWorkspaceEntityInternalApi()
    section("internal object ${MetadataStorage.IMPL_NAME}: ${MetadataStorage.base}()") {
      val builtTypes: MutableList<String> = arrayListOf()
      val builtPrimitiveTypes = linkedSetOf<BuiltPrimitiveType>()

      for (abstractType in abstractTypes) {
        buildAbstractTypeMetadata(abstractType, builtTypes, builtPrimitiveTypes)
      }

      for (type in types) {
        buildObjClassMetadata(type, builtTypes, builtPrimitiveTypes)
      }

      section("override fun initializeMetadata()") {
        for (primitiveType in builtPrimitiveTypes) {
          +"val ${primitiveType.getVariableName()} = ${primitiveType.getConstructor()}"
        }
        +"var typeMetadata: $StorageTypeMetadata"
        for (metaDataString in builtTypes) {
          +"typeMetadata = $metaDataString"
          +"${MetadataStorage.addMetadata}(typeMetadata)"
        }
      }

      section("override fun initializeMetadataHash()") {
        val jvmClassesToBuild = linkedMapOf<String, ValueType.JvmClass<*>>()

        types.forEach { collectJvmClasses(it, jvmClassesToBuild) }
        abstractTypes.forEach { collectJvmClasses(it, jvmClassesToBuild) }

        val entityHashComputer = EntityMetadataHashComputer(builtPrimitiveTypes)
        val classHashComputer = ClassMetadataHashComputer(builtPrimitiveTypes)

        val hashWithTypeFqn = arrayListOf<Pair<String, MetadataHash>>()
        hashWithTypeFqn.addAll(types.map { getFullName(it) to entityHashComputer.computeHash(this@metadataStorageImplCode, it) })
        hashWithTypeFqn.addAll(jvmClassesToBuild.map {
          getName(it.value) to classHashComputer.computeHash(this@metadataStorageImplCode,
                                                             it.value)
        })

        for((typeFqn, metadataHash) in hashWithTypeFqn) {
          +"${MetadataStorage.addMetadataHash}(typeFqn = $typeFqn, metadataHash = $metadataHash)"
        }
      }
    }
  }
}

private fun GeneratorContext.buildObjClassMetadata(
  objClass: ObjClass<*>,
  builtTypes: MutableList<String>,
  builtPrimitiveTypes: MutableSet<BuiltPrimitiveType>,
) {
  val entityMetadataBuilder = EntityMetadataBuilder(builtPrimitiveTypes)
  builtTypes.add(entityMetadataBuilder.buildMetadata(this, objClass))
}

private fun GeneratorContext.buildAbstractTypeMetadata(
  type: ValueType.AbstractClass<*>,
  builtTypes: MutableList<String>,
  builtPrimitiveTypes: MutableSet<BuiltPrimitiveType>,
) {
  val classBuilder = ClassMetadataBuilder.newInstance(builtPrimitiveTypes)
  builtTypes.addAll(type.allFinalSubClasses.map { classBuilder.buildMetadata(this@buildAbstractTypeMetadata, it) })
}

// HASH REQUIRED
private fun GeneratorContext.collectJvmClasses(objClass: ObjClass<*>, jvmClasses: MutableMap<String, ValueType.JvmClass<*>>) {
  getAllPropertiesWithOwnExtensions(objClass).forEach {
    collectJvmClasses(it.valueType, jvmClasses)
  }
}

// HASH REQUIRED
private fun GeneratorContext.collectJvmClasses(valueType: ValueType<*>, jvmClasses: MutableMap<String, ValueType.JvmClass<*>>) {
  if (valueType is ValueType.JvmClass<*> && valueType !is ValueType.Blob<*>) {
    jvmClasses[getName(valueType)] = valueType
  }
  when (valueType) {
    is ValueType.AbstractClass -> valueType.subclasses.forEach { collectJvmClasses(it, jvmClasses) }
    is ValueType.FinalClass<*> -> valueType.properties.forEach { collectJvmClasses(it.valueType, jvmClasses) }
    is ValueType.Object<*> -> valueType.properties.forEach { collectJvmClasses(it.valueType, jvmClasses) }
    is ValueType.Enum<*> -> valueType.properties.forEach { collectJvmClasses(it.valueType, jvmClasses) }
    is ValueType.Optional<*> -> collectJvmClasses(valueType.type, jvmClasses)
    is ValueType.Collection<*, *> -> collectJvmClasses(valueType.elementType, jvmClasses)
    is ValueType.Map<*, *> -> {
      collectJvmClasses(valueType.keyType, jvmClasses)
      collectJvmClasses(valueType.valueType, jvmClasses)
    }
    else -> return
  }
}