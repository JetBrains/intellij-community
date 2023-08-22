// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl

import com.intellij.platform.workspace.storage.CodeGeneratorVersions
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.WorkspaceEntity

internal object GeneratedCodeCompatibilityChecker {
  fun checkCode(entity: Class<WorkspaceEntity>) {
    val builderClass: Class<*>

    if (CodeGeneratorVersions.checkApiInInterface) { // Check that builder interface has the correct api version
      builderClass = entity.toBuilderClass()
      val annotations = builderClass.annotations
      val entityApiVersion = annotations.filterIsInstance<GeneratedCodeApiVersion>().singleOrNull()?.version
                             ?: error("Generated interface '$builderClass' doesn't have an api version marker. " +
                                      "You should regenerate the code of your entities")
      assert(entityApiVersion == CodeGeneratorVersions.API_VERSION) {
        """
          Current API version of the generator is '${CodeGeneratorVersions.API_VERSION}',
          but the generated code is marked as version '$entityApiVersion'.
          Please, regenerate your entities.
          
          Checked entity: $builderClass
        """.trimIndent()
      }
    }

    val implClass = entity.toImplClass()
    val implAnnotations = implClass.annotations
    if (CodeGeneratorVersions.checkApiInImpl) { // Check that impl class has the correct api version
      val entityImplApiVersion = implAnnotations.filterIsInstance<GeneratedCodeApiVersion>().singleOrNull()?.version
                                 ?: error("Generated class '$implClass' doesn't have an api version marker. " +
                                          "You should regenerate the code of your entities")
      assert(entityImplApiVersion == CodeGeneratorVersions.API_VERSION) {
        """
          Current API version of the generator is '${CodeGeneratorVersions.API_VERSION}',
          but the generated code is marked as version '$entityImplApiVersion'.
          Please, regenerate your entities.
          
          Checked entity: $implClass
        """.trimIndent()
      }
    }

    if (CodeGeneratorVersions.checkImplInImpl) { // Check that impl class has the correct impl version
      val entityImplImplVersion = implAnnotations.filterIsInstance<GeneratedCodeImplVersion>().singleOrNull()?.version
                                 ?: error("Generated class '$implClass' doesn't have an impl version marker. " +
                                          "You should regenerate the code of your entities")
      assert(entityImplImplVersion == CodeGeneratorVersions.IMPL_VERSION) {
        """
          Current IMPL version of the generator is '${CodeGeneratorVersions.IMPL_VERSION}',
          but the generated code is marked as version '$entityImplImplVersion'.
          Please, regenerate your entities.
          
          Checked entity: $implClass
        """.trimIndent()
      }
    }
  }
  
  // Something more stable?
  private fun Class<WorkspaceEntity>.toBuilderClass(): Class<*> {
    return loadClassByName("$name\$Builder", classLoader)
  }

  private fun Class<WorkspaceEntity>.toImplClass(): Class<*> {
    return loadClassByName(name + "Impl", classLoader)
  }
}