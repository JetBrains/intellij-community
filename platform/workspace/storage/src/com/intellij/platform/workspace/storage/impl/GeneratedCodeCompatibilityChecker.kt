// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl

import com.intellij.platform.workspace.storage.CodeGeneratorVersions
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import java.util.concurrent.ConcurrentHashMap

internal object GeneratedCodeCompatibilityChecker {
  private data class CheckSettings(
    val apiVersion: Int,
    val implVersion: Int,
    val checkApiInInterface: Boolean,
    val checkApiInImpl: Boolean,
    val checkImplInImpl: Boolean,
  )

  private val checkedBuilderClasses = ConcurrentHashMap.newKeySet<Class<*>>()

  @Volatile
  private var checkedSettings: CheckSettings? = null

  fun checkCode(implBuilderClass: Class<*>, implClass: Class<*>) {
    val settings = currentSettings()
    if (settings != checkedSettings) {
      checkedBuilderClasses.clear()
      checkedSettings = settings
    }
    if (implBuilderClass in checkedBuilderClasses) return
    doCheckCode(implBuilderClass, implClass, settings)
    checkedBuilderClasses.add(implBuilderClass)
  }

  private fun currentSettings(): CheckSettings = CheckSettings(
    apiVersion = CodeGeneratorVersions.API_VERSION,
    implVersion = CodeGeneratorVersions.IMPL_VERSION,
    checkApiInInterface = CodeGeneratorVersions.checkApiInInterface,
    checkApiInImpl = CodeGeneratorVersions.checkApiInImpl,
    checkImplInImpl = CodeGeneratorVersions.checkImplInImpl,
  )

  private fun doCheckCode(implBuilderClass: Class<*>, implClass: Class<*>, settings: CheckSettings) {
    if (settings.checkApiInInterface) { // Check that builder interface has the correct api version
      val builderClass = implBuilderClass.toBuilderInterface()
      val entityApiVersion = builderClass.getAnnotation(GeneratedCodeApiVersion::class.java)?.version
                             ?: error("Generated interface '$builderClass' doesn't have an api version marker. " +
                                      "You should regenerate the code of your entities")
      assert(entityApiVersion == settings.apiVersion) {
        """
          Current API version of the generator is '${settings.apiVersion}',
          but the generated code is marked as version '$entityApiVersion'.
          Please, regenerate your entities.
          
          Checked entity: $builderClass
        """.trimIndent()
      }
    }

    if (settings.checkApiInImpl) { // Check that impl class has the correct api version
      val entityImplApiVersion = implClass.getAnnotation(GeneratedCodeApiVersion::class.java)?.version
                                 ?: error("Generated class '$implClass' doesn't have an api version marker. " +
                                          "You should regenerate the code of your entities")
      assert(entityImplApiVersion == settings.apiVersion) {
        """
          Current API version of the generator is '${settings.apiVersion}',
          but the generated implementation code is marked as version '$entityImplApiVersion'.
          Please, regenerate your entities.
          
          Checked entity: $implClass
        """.trimIndent()
      }
    }

    if (settings.checkImplInImpl) { // Check that impl class has the correct impl version
      val entityImplImplVersion = implClass.getAnnotation(GeneratedCodeImplVersion::class.java)?.version
                                 ?: error("Generated class '$implClass' doesn't have an impl version marker. " +
                                          "You should regenerate the code of your entities")
      assert(entityImplImplVersion == settings.implVersion) {
        """
          Current IMPL version of the generator is '${settings.implVersion}',
          but the generated code is marked as version '$entityImplImplVersion'.
          Please, regenerate your entities.
          
          Checked entity: $implClass
        """.trimIndent()
      }
    }
  }

  private fun Class<*>.toBuilderInterface(): Class<*> {
    var candidate = interfaces.singleOrNull()
                    ?: error("Generated builder '$this' doesn't implement a builder interface. " +
                             "You should regenerate the code of your entities")
    // compatibility builders are not annotated themselves and extend the annotated builder interface
    while (candidate.getAnnotation(GeneratedCodeApiVersion::class.java) == null) {
      candidate = candidate.interfaces.singleOrNull() ?: break
    }
    return candidate
  }
}