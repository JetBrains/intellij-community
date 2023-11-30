// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer.classes

import com.intellij.workspaceModel.codegen.deft.meta.CompiledObjModule
import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.metadata.*
import com.intellij.workspaceModel.codegen.impl.metadata.BuiltPrimitiveType
import com.intellij.workspaceModel.codegen.impl.metadata.EntityMetadataBuilder
import com.intellij.workspaceModel.codegen.impl.metadata.PropertyMetadataBuilder
import com.intellij.workspaceModel.codegen.impl.writer.*
import com.intellij.workspaceModel.codegen.impl.writer.MetadataStorage
import com.intellij.workspaceModel.codegen.impl.writer.extensions.*
import com.intellij.workspaceModel.codegen.impl.writer.extensions.allFinalSubClasses
import com.intellij.workspaceModel.codegen.impl.writer.extensions.generatedCodeVisibilityModifier

internal fun implWsMetadataStorageCode(module: CompiledObjModule, types: List<ObjClass<*>>,
                                       abstractTypes: List<ValueType.AbstractClass<*>>): String = lines {
  line("package ${module.name}")
  line()
  section("${module.generatedCodeVisibilityModifier} object ${MetadataStorage.IMPL_NAME}: ${MetadataStorage.base}()") {
    val builtTypes: MutableList<String> = arrayListOf()
    val builtPrimitiveTypes = linkedSetOf<BuiltPrimitiveType>()

    abstractTypes.forEach {
      buildAbstractTypeMetadata(it, builtTypes, builtPrimitiveTypes)
    }

    types.forEach {
      buildObjClassMetadata(it, builtTypes, builtPrimitiveTypes)
    }

    sectionNl("override fun initializeMetadata()") {
      builtPrimitiveTypes.forEach {
        line("val ${it.getVariableName()} = ${it.getConstructor()}")
      }
      line()
      line("var typeMetadata: $StorageTypeMetadata")
      builtTypes.forEach {
        line()
        line("typeMetadata = $it")
        line()
        line("${MetadataStorage.addMetadata}(typeMetadata)")
      }
    }

    sectionNl("override fun initializeMetadataHash()") {
      val jvmClassesToBuild = linkedMapOf<String, ValueType.JvmClass<*>>()

      types.forEach { it.collectJvmClasses(jvmClassesToBuild) }
      abstractTypes.forEach { it.collectJvmClasses(jvmClassesToBuild) }

      val entityHashComputer = EntityMetadataHashComputer(builtPrimitiveTypes)
      val classHashComputer = ClassMetadataHashComputer(builtPrimitiveTypes)

      val hashWithTypeFqn = arrayListOf<Pair<String, MetadataHash>>()
      hashWithTypeFqn.addAll(types.map { it.fullName to entityHashComputer.computeHash(it) })
      hashWithTypeFqn.addAll(jvmClassesToBuild.map { it.value.name to classHashComputer.computeHash(it.value) })

      list(hashWithTypeFqn) {
        "${MetadataStorage.addMetadataHash}(typeFqn = ${this.first}, metadataHash = ${this.second})"
      }
    }
  }
}


internal fun CompiledObjModule.implWsMetadataStorageBridgeCode(metadataStorageImpl: QualifiedName): String = lines {
  line("package $name")
  line()
  line("$generatedCodeVisibilityModifier object ${MetadataStorage.IMPL_NAME}: ${MetadataStorage.bridge}($metadataStorageImpl)")
}


private fun buildObjClassMetadata(
  objClass: ObjClass<*>,
  builtTypes: MutableList<String>,
  builtPrimitiveTypes: MutableSet<BuiltPrimitiveType>
) {
  val entityMetadataBuilder = EntityMetadataBuilder(builtPrimitiveTypes)
  builtTypes.add(entityMetadataBuilder.buildMetadata(objClass))
}

private fun buildAbstractTypeMetadata(
  type: ValueType.AbstractClass<*>,
  builtTypes: MutableList<String>,
  builtPrimitiveTypes: MutableSet<BuiltPrimitiveType>
) {
  val classBuilder = ClassMetadataBuilder.newInstance(builtPrimitiveTypes)
  builtTypes.addAll(type.allFinalSubClasses.map { classBuilder.buildMetadata(it) })
}


private fun ObjClass<*>.collectJvmClasses(jvmClasses: MutableMap<String, ValueType.JvmClass<*>>) {
  allFieldsWithOwnExtensions.forEach {
    it.valueType.collectJvmClasses(jvmClasses)
  }
}

private fun ValueType<*>.collectJvmClasses(jvmClasses: MutableMap<String, ValueType.JvmClass<*>>) {
  if (this is ValueType.JvmClass<*> && this !is ValueType.Blob<*>) {
    if (jvmClasses.containsKey(javaClassName)) {
      return
    }
    jvmClasses[javaClassName] = this
  }
  when (this) {
    is ValueType.AbstractClass -> subclasses.forEach { it.collectJvmClasses(jvmClasses) }
    is ValueType.FinalClass<*> -> properties.forEach { it.valueType.collectJvmClasses(jvmClasses) }
    is ValueType.Object<*> -> properties.forEach { it.valueType.collectJvmClasses(jvmClasses) }
    is ValueType.Enum<*> -> properties.forEach { it.valueType.collectJvmClasses(jvmClasses) }
    is ValueType.Optional<*> -> type.collectJvmClasses(jvmClasses)
    is ValueType.Collection<*, *> -> elementType.collectJvmClasses(jvmClasses)
    is ValueType.Map<*, *> -> {
      keyType.collectJvmClasses(jvmClasses)
      valueType.collectJvmClasses(jvmClasses)
    }
    else -> return
  }
}