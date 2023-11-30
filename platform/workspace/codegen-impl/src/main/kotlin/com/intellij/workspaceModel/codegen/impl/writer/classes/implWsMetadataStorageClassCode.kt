// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer.classes

import com.intellij.workspaceModel.codegen.deft.meta.CompiledObjModule
import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.metadata.BuiltPrimitiveType
import com.intellij.workspaceModel.codegen.impl.metadata.EntityMetadataBuilder
import com.intellij.workspaceModel.codegen.impl.metadata.PropertyMetadataBuilder
import com.intellij.workspaceModel.codegen.impl.writer.*
import com.intellij.workspaceModel.codegen.impl.writer.MetadataStorage
import com.intellij.workspaceModel.codegen.impl.writer.extensions.allFinalSubClasses
import com.intellij.workspaceModel.codegen.impl.writer.extensions.generatedCodeVisibilityModifier

val CompiledObjModule.implWsMetadataStorageClassCode: String?
get() {
  if (abstractTypes.isEmpty() && types.isEmpty()) {
    return null
  }

  return lines {
    line("package $name")
    line()
    section("$generatedCodeVisibilityModifier object ${MetadataStorage.IMPL_NAME}: ${MetadataStorage.base}()") {
      section("init") {
        val builtTypes: MutableList<String> = arrayListOf()
        val builtPrimitiveTypes: MutableSet<BuiltPrimitiveType> = LinkedHashSet()

        abstractTypes.forEach {
          buildAbstractTypeMetadata(it, builtTypes, builtPrimitiveTypes)
        }

        types.forEach {
          buildObjClassMetadata(it, builtTypes, builtPrimitiveTypes)
        }

        line()
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
    }
  }
}

private fun buildObjClassMetadata(objClass: ObjClass<*>,
                                  builtTypes: MutableList<String>,
                                  builtPrimitiveTypes: MutableSet<BuiltPrimitiveType>) {
  val entityMetadataBuilder = EntityMetadataBuilder(builtPrimitiveTypes)
  builtTypes.add(entityMetadataBuilder.buildMetadata(objClass))
}

private fun buildAbstractTypeMetadata(type: ValueType.AbstractClass<*>,
                                      builtTypes: MutableList<String>,
                                      builtPrimitiveTypes: MutableSet<BuiltPrimitiveType>) {
  val classBuilder = PropertyMetadataBuilder(builtPrimitiveTypes).classBuilder
  type.allFinalSubClasses.forEach { builtTypes.add(classBuilder.buildMetadata(it)) }
}