// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder

import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages
import com.intellij.gradle.toolingExtension.impl.util.GradleConventionUtil
import com.intellij.gradle.toolingExtension.impl.util.GradleConventionUtil.getConventionPlugins
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import groovy.lang.Closure
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.reflect.HasPublicType
import org.jetbrains.plugins.gradle.model.*
import org.jetbrains.plugins.gradle.tooling.Message
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService

/**
 * @author Vladislav.Soroka
 */
class ProjectExtensionsDataBuilderImpl : ModelBuilderService {

  override fun canBuild(modelName: String): Boolean {
    return modelName == GradleExtensions::class.java.name
  }

  override fun buildAll(modelName: String, project: Project): Any {
    val result = DefaultGradleExtensions()
    result.parentProjectPath = project.parent?.path

    result.configurations.addAll(collectConfigurations(project.configurations, false))
    result.configurations.addAll(collectConfigurations(project.buildscript.configurations, true))
    result.conventions.addAll(collectConventions(project))

    val extensions = project.extensions
    extensions.extraProperties.properties.entries.forEach { entry ->
      if (entry.key == "extraModelBuilder" || entry.key.contains(".")) return@forEach
      val typeFqn = getType(entry.value)
      result.gradleProperties.add(DefaultGradleProperty(entry.key, typeFqn))
    }

    for (it in DefaultGroovyMethods.findAll(extensions)) {
      val extension = it as ExtensionContainer
      val keyList = extractKeys(extension)

      for (name in keyList) {
        val value = extension.findByName(name)
        if (value == null) continue

        val rootTypeFqn = getType(value)
        result.extensions.add(DefaultGradleExtension(name, rootTypeFqn))
      }
    }
    return result
  }

  private fun extractKeys(extension: ExtensionContainer): List<String> {
    val result = mutableListOf<String>()
    for (schema in extension.extensionsSchema) {
      result.add(schema.name)
    }
    return result
  }

  override fun reportErrorMessage(
    modelName: String,
    project: Project,
    context: ModelBuilderContext,
    exception: Exception
  ) {
    context.getMessageReporter().createMessage()
      .withGroup(Messages.PROJECT_EXTENSION_MODEL_GROUP)
      .withKind(Message.Kind.WARNING)
      .withTitle("Project extensions data import failure")
      .withText("Unable to resolve some context data of gradle scripts. Some codeInsight features inside *.gradle files can be unavailable.")
      .withException(exception)
      .reportMessage(project)
  }

  companion object {
    private fun collectConfigurations(
      configurations: ConfigurationContainer,
      scriptClasspathConfiguration: Boolean
    ): List<DefaultGradleConfiguration> {
      val result = mutableListOf<DefaultGradleConfiguration>()
      for (configurationName in configurations.names) {
        val configuration = configurations.getByName(configurationName)
        val description = configuration.description
        val visible = configuration.isVisible
        val declarationAlternatives = getDeclarationAlternatives(configuration)
        result.add(DefaultGradleConfiguration(configurationName, description, visible, scriptClasspathConfiguration, declarationAlternatives))
      }
      return result
    }

    private fun getDeclarationAlternatives(configuration: Configuration): List<String> {
      try {
        val method = configuration.javaClass.getMethod("getDeclarationAlternatives")
        @Suppress("UNCHECKED_CAST")
        return method.invoke(configuration) as? List<String> ?: emptyList()
      }
      catch (e: NoSuchMethodException) {
        return emptyList()
      }
      catch (e: SecurityException) {
        return emptyList()
      }
    }

    private fun collectConventions(project: Project): List<DefaultGradleConvention> {
      if (GradleVersionUtil.isCurrentGradleAtLeast("8.2")) {
        return emptyList()
      }
      val result = mutableListOf<DefaultGradleConvention>()
      if (GradleConventionUtil.isGradleConventionsSupported()) {
        getConventionPlugins(project).forEach { (key, value) ->
          result.add(DefaultGradleConvention(key, getType(value)))
        }
      }
      return result
    }

    @JvmStatic
    fun getType(obj: Any?): String? {
      if (obj is HasPublicType) {
        return obj.publicType.toString()
      }
      val clazz = obj?.javaClass?.canonicalName
      val decorIndex = clazz?.lastIndexOf("_Decorated")
      val result = if (decorIndex == null || decorIndex == -1) clazz else clazz.substring(0, decorIndex)
      if (result == null && obj is Closure<*>) return "groovy.lang.Closure"
      return result
    }
  }
}