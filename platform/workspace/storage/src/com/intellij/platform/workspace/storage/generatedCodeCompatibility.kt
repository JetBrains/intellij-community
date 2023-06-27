// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage

import com.intellij.platform.workspace.storage.impl.loadClassByName
import org.jetbrains.annotations.TestOnly

object CodeGeneratorVersions {
  /** these constants are accessed from WorkspaceImplObsoleteInspection */
  private const val API_VERSION_INTERNAL = 2
  private const val IMPL_MAJOR_VERSION_INTERNAL = 2
  private const val IMPL_MINOR_VERSION_INTERNAL = 0

  @set:TestOnly
  var API_VERSION = API_VERSION_INTERNAL
  @set:TestOnly
  var IMPL_VERSION = IMPL_MAJOR_VERSION_INTERNAL

  var checkApiInInterface = true
  var checkApiInImpl = true
  var checkImplInImpl = true
}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class GeneratedCodeApiVersion(val version: Int)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class GeneratedCodeImplVersion(val version: Int)


object GeneratedCodeCompatibilityChecker {
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
}

// Something more stable?
private fun Class<WorkspaceEntity>.toBuilderClass(): Class<*> {
  return loadClassByName("$name\$Builder", classLoader)
}

private fun Class<WorkspaceEntity>.toImplClass(): Class<*> {
  return loadClassByName(name + "Impl", classLoader)
}
