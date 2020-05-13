// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions.localWhitelist

import com.intellij.internal.statistic.eventLog.validator.rules.impl.TestModeValidationRule
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType

internal class LocalWhitelistJsonSchemaProviderFactory : JsonSchemaProviderFactory {
  override fun getProviders(project: Project): List<JsonSchemaFileProvider> = listOf(LocalWhitelistJsonSchemaFileProvider())

  private class LocalWhitelistJsonSchemaFileProvider : JsonSchemaFileProvider {
    override fun isAvailable(file: VirtualFile): Boolean {
      val isValidationRulesFile = file.getUserData(LOCAL_WHITELIST_VALIDATION_RULES_KEY) ?: false
      return isValidationRulesFile && TestModeValidationRule.isTestModeEnabled()
    }

    override fun getName(): String = "Local Whitelist"

    override fun getSchemaFile(): VirtualFile? = JsonSchemaProviderFactory.getResourceFile(
      LocalWhitelistJsonSchemaProviderFactory::class.java, "/schemas/local-whitelist.schema.json")

    override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema
  }

  companion object {
    val LOCAL_WHITELIST_VALIDATION_RULES_KEY = Key.create<Boolean>("statistics.localWhitelist.validation.rules.file")
  }
}