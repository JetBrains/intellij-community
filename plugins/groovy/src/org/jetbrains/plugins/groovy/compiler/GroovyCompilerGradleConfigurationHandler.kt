// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.compiler

import com.intellij.openapi.compiler.options.ExcludeEntryDescription
import com.intellij.openapi.compiler.options.ExcludedEntriesConfiguration
import com.intellij.openapi.externalSystem.model.project.settings.ConfigurationData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.settings.ConfigurationHandler
import com.intellij.openapi.project.Project

class GroovyCompilerGradleConfigurationHandler : ConfigurationHandler {

  override fun apply(project: Project, modelsProvider: IdeModifiableModelsProvider, configuration: ConfigurationData) {
    val obj = configuration.find("groovyCompiler") as? Map<*, *> ?: return
    val excludes = obj["excludes"] as? List<*> ?: return
    val compilerConfiguration = GroovyCompilerConfiguration.getInstance(project)
    val excludesConfig = compilerConfiguration.excludeFromStubGeneration as ExcludedEntriesConfiguration
    for (o in excludes) {
      val exclude = o as? Map<*, *> ?: continue
      val fileUrl = exclude["url"] as? String ?: continue
      val includeSubdirectories = exclude["includeSubdirectories"] as? Boolean ?: continue
      val isFile = exclude["isFile"] as? Boolean ?: continue
      val entry = ExcludeEntryDescription(fileUrl, includeSubdirectories, isFile, excludesConfig)
      excludesConfig.addExcludeEntryDescription(entry)
    }
  }
}
