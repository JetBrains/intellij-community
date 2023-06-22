// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.properties.base.BasePropertiesFile
import org.jetbrains.plugins.gradle.properties.GradleLocalProperties.EMPTY
import org.jetbrains.plugins.gradle.properties.models.Property
import java.nio.file.Path
import java.nio.file.Paths

const val GRADLE_LOCAL_JAVA_HOME_PROPERTY = "java.home"
const val GRADLE_LOCAL_PROPERTIES_FILE_NAME = "config.properties"

object GradleLocalPropertiesFile : BasePropertiesFile<GradleLocalProperties>() {

  override val propertiesFileName = GRADLE_LOCAL_PROPERTIES_FILE_NAME

  override fun getProperties(project: Project, externalProjectPath: Path): GradleLocalProperties {
    val propertiesPath = getGradleLocalPropertiesPath(externalProjectPath)
    return loadGradleLocalProperties(propertiesPath)
  }

  private fun loadGradleLocalProperties(propertiesPath: Path): GradleLocalProperties {
    val properties = loadProperties(propertiesPath) ?: return EMPTY
    val javaHome = properties.getProperty(GRADLE_LOCAL_JAVA_HOME_PROPERTY)
    val javaHomeProperty = javaHome?.let { Property(it, propertiesPath.toString()) }
    return GradleLocalPropertiesImpl(javaHomeProperty)
  }

  private fun getGradleLocalPropertiesPath(externalProjectPath: Path): Path {
    val gradleLocalPath = externalProjectPath.resolve(Paths.get(GRADLE_CACHE_DIR_NAME, propertiesFileName))
    return gradleLocalPath.toAbsolutePath().normalize()
  }
}