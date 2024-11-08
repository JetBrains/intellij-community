// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder

import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import groovy.lang.Closure
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.plugins.ExtensionAware
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
    extensions.extraProperties.properties.forEach { name, value ->
      if (name == "extraModelBuilder" || name.contains(".")) return@forEach
      val typeFqn = getType(value)
      result.gradleProperties.add(DefaultGradleProperty(name, typeFqn))
    }

    extractExtensions(extensions, "", result, null, 0)
    return result
  }

  private fun extractExtensionWithKey(
    extensions: ExtensionContainer,
    key: String,
    namePrefix: String,
    result: DefaultGradleExtensions,
    depth: Int
  ) {
    //Extensions can be recursive, so we need to limit the depth of the recursion
    if (depth > MAX_EXTENSION_RECURSION) return

    //Get the extension
    val extension = extensions.findByName(key) ?: return

    //Register the extension
    result.extensions.add(DefaultGradleExtension("$namePrefix$key", getType(extension)))

    //Create the new prefix for the next level of recursion
    val newPrefix = "$namePrefix$key."

    //We want to know if the extension is a collection, so we can extract the inner extensions of its elements behind a wildcard.
    if (extension is NamedDomainObjectCollection<*>) {
      extractExtensionsFromCollection(extension, newPrefix, result, depth)
    }

    //If the extension is an ExtensionAware, we can extract its inner extensions
    if (extension is ExtensionAware) {
      extractInnerExtensions(extension, newPrefix, result, depth)
    }
  }

  private fun extractInnerExtensions(source: ExtensionAware, namePrefix: String, result: DefaultGradleExtensions, depth: Int) {
    extractExtensions(source.extensions, namePrefix, result, null, depth)
  }

  private fun extractExtensions(extension: ExtensionContainer, namePrefix: String, result: DefaultGradleExtensions, keyFilter: Set<String>?, depth: Int) {
    val keyList = extractKeys(extension, keyFilter)

    for (name in keyList) {
      if (keyFilter != null && !keyFilter.contains(name)) {
        continue
      }

      extractExtensionWithKey(extension, name, namePrefix, result, depth + 1)
    }
  }

  private fun extractKeys(extension: ExtensionContainer, keyFilter: Set<String>?): Set<String> {
    val result = mutableSetOf<String>()
    for (schema in extension.extensionsSchema) {
      //If we have a key filter then we should only extract the keys that are in the filter
      if (keyFilter != null && !keyFilter.contains(schema.name)) continue
      result.add(schema.name)
    }
    return result
  }

  private fun extractExtensionsFromCollection(source: NamedDomainObjectCollection<*>, namePrefix: String, result: DefaultGradleExtensions, depth: Int) {
    var keyFilter: Set<String>? = null;
    var sourceObject: ExtensionAware? = null
    for (it in DefaultGroovyMethods.findAll(source)) {
      //For now, we don't support nested collections
      //The logic to process this would become rather contrived and complicated,
      //because we would need to do a deep recursion to collect all possible keys, and ensure that they are
      //all the same between all possible collection instances, this would get messy very quickly.
      //The UX and speed of the feature would be negatively impacted.
      //TODO (marchermans): Deal with recursive collections, possibly after we dealt with different extensions on different elements
      if (it !is ExtensionAware) continue

      if (keyFilter == null) {
        //First object found that is an ExtensionAware, we can use it as the source object
        keyFilter = extractKeys(it.extensions, null)
        sourceObject = it
        continue;
      }

      //If we have a key filter, we should intersect it with the keys of the current object
      //We don't want to extract keys that are not present in all objects
      //TODO (marchermans): We need to expand the UX here further, it is technically feasible to support different extensions on different elements.
      val keys = extractKeys(it.extensions, null)
      keyFilter = keyFilter.intersect(keys)
    }

    if (sourceObject == null) return
    if (keyFilter == null) return
    if (keyFilter.isEmpty()) return

    extractExtensions(sourceObject.extensions, "$namePrefix*.", result, keyFilter, depth)
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
    const val MAX_EXTENSION_RECURSION = 15;

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
      @Suppress("DEPRECATION")
      project.convention.plugins.forEach { (key, value) ->
        result.add(DefaultGradleConvention(key, getType(value)))
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