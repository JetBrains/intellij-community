// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ImportOldConfigsState
import com.intellij.openapi.application.ImportOldConfigsState.InitialImportScenario.*
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.diagnostic.thisLogger

enum class ExternalSettingsImportScenario {
  NoImport,
  ImportedFromThirdPartyProduct,
  ImportFailed
}

internal object ExternalSettingsImportSupport {

  @JvmField
  @Suppress("MayBeConstant")
  val CUSTOM_IMPORT_CLASS = "intellij.custom.importer"
  private val logger = thisLogger()

  /**
   * Performs settings import from external IDE.
   */
  @JvmStatic
  fun importSettings(): ImportOldConfigsState.InitialImportScenario {
    val scenario = logger.runAndLogException block@{
      val className = System.getProperty(CUSTOM_IMPORT_CLASS) ?: return@block null
      val importerClass = Class.forName(className) ?: run {
        logger.warn("Could not find class $className")
        return@block null
      }

      val methodName = "importSettings"
      val method = importerClass.getMethod(methodName) ?: run {
        logger.warn("Could not find method $methodName on class $importerClass")
        return@block null
      }

      method.invoke(null) as ExternalSettingsImportScenario
    } ?: ExternalSettingsImportScenario.ImportFailed

    return scenario.toInitialImportScenario()
  }

  private fun ExternalSettingsImportScenario.toInitialImportScenario(): ImportOldConfigsState.InitialImportScenario =
    when(this) {
      ExternalSettingsImportScenario.NoImport -> CLEAN_CONFIGS
      ExternalSettingsImportScenario.ImportedFromThirdPartyProduct -> IMPORTED_FROM_THIRD_PARTY
      ExternalSettingsImportScenario.ImportFailed -> IMPORT_FROM_THIRD_PARTY_FAILED
    }
}