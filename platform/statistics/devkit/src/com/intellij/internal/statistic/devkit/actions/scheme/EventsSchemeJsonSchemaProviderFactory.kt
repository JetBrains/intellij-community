// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.actions.scheme

import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType

internal class EventsSchemeJsonSchemaProviderFactory : JsonSchemaProviderFactory {
  override fun getProviders(project: Project): List<JsonSchemaFileProvider> = listOf(EventsSchemeJsonSchemaFileProvider())

  private class EventsSchemeJsonSchemaFileProvider : JsonSchemaFileProvider {
    override fun isAvailable(file: VirtualFile): Boolean {
      val isValidationRulesFile = file.getUserData(EVENTS_TEST_SCHEME_VALIDATION_RULES_KEY) ?: false
      return isValidationRulesFile && StatisticsRecorderUtil.isAnyTestModeEnabled()
    }

    override fun getName(): String = "Events Test Scheme"

    override fun getSchemaFile(): VirtualFile? = JsonSchemaProviderFactory.getResourceFile(
      EventsSchemeJsonSchemaProviderFactory::class.java, "/schemas/events-test-scheme.schema.json")

    override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema
  }

  companion object {
    val EVENTS_TEST_SCHEME_VALIDATION_RULES_KEY = Key.create<Boolean>("statistics.events.test.scheme.validation.rules.file")
  }
}