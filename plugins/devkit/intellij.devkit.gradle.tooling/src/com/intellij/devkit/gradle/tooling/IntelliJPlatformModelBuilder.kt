// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.gradle.tooling

import org.gradle.api.Project
import org.gradle.tooling.model.Model
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService
import org.jetbrains.plugins.gradle.tooling.Message
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import java.io.Serializable
import java.lang.reflect.ParameterizedType

private const val INTELLIJ_PLATFORM_EXTENSION_NAME = "intellijPlatform"
private const val INTELLIJ_PLATFORM_DEPENDENCY_CONFIGURATION_CLASS =
  "org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependencyConfiguration"
private const val INTELLIJ_PLATFORM_TYPE_CLASS = "org.jetbrains.intellij.platform.gradle.IntelliJPlatformType"
private const val DUMP_PRODUCTS_RELEASES_TASK_NAME = "dumpProductsReleases"
private const val GRADLE_ACTION_CLASS = "org.gradle.api.Action"
private const val PRODUCT_CODE_GETTER = "getCode"

/**
 * Transfers IntelliJ Platform dependency helpers and the generated product-release catalog from Gradle to the IDE.
 */
@ApiStatus.Internal
interface IntelliJPlatformGradleModel : Model, Serializable {
  val dependencyHelperProductCodes: Map<String, String>
  val productReleasesFile: String?
}

internal class IntelliJPlatformGradleModelImpl(
  override val dependencyHelperProductCodes: Map<String, String>,
  override val productReleasesFile: String?,
) : IntelliJPlatformGradleModel

/**
 * Reflects over the IntelliJ Platform Gradle Plugin DSL and schedules its release-catalog dump for IDE import.
 */
@ApiStatus.Internal
class IntelliJPlatformModelBuilder : AbstractModelBuilderService() {

  override fun canBuild(modelName: String?): Boolean {
    return modelName == IntelliJPlatformGradleModel::class.java.name
  }

  override fun buildAll(modelName: String, project: Project, context: ModelBuilderContext): Any? {
    val extension = project.dependencies.extensions.findByName(INTELLIJ_PLATFORM_EXTENSION_NAME) ?: return null
    val dumpTask = project.tasks.findByName(DUMP_PRODUCTS_RELEASES_TASK_NAME)

    if (dumpTask != null) {
      project.gradle.startParameter.setTaskNames(
        project.gradle.startParameter.taskNames.toSet() + dumpTask.path,
      )
    }

    return IntelliJPlatformGradleModelImpl(
      dependencyHelperProductCodes = extension.javaClass.loadDependencyHelperProductCodes(),
      productReleasesFile = dumpTask?.outputs?.files?.singleFile?.absolutePath,
    )
  }

  override fun reportErrorMessage(modelName: String, project: Project, context: ModelBuilderContext, exception: Exception) {
    context.messageReporter.createMessage()
      .withGroup(this)
      .withKind(Message.Kind.WARNING)
      .withTitle("Gradle import errors")
      .withText("Unable to build IntelliJ Platform project configuration")
      .withException(exception)
      .reportMessage(project)
  }

  private fun Class<*>.loadDependencyHelperProductCodes(): Map<String, String> {
    val platformTypeClass = classLoader.loadClass(INTELLIJ_PLATFORM_TYPE_CLASS)
    val productCodeGetter = platformTypeClass.getMethod(PRODUCT_CODE_GETTER)
    val productCodeByTypeName = platformTypeClass.enumConstants.associate { platformType ->
      (platformType as Enum<*>).name.lowercase() to productCodeGetter.invoke(platformType) as String
    }

    return methods.asSequence()
      .filter { method ->
        method.genericParameterTypes.any { parameterType ->
          parameterType is ParameterizedType &&
          parameterType.rawType.typeName == GRADLE_ACTION_CLASS &&
          parameterType.actualTypeArguments.any { it.typeName == INTELLIJ_PLATFORM_DEPENDENCY_CONFIGURATION_CLASS }
        }
      }
      .mapNotNull { method ->
        productCodeByTypeName[method.name.lowercase()]?.let { method.name to it }
      }
      .toMap(sortedMapOf())
  }
}
