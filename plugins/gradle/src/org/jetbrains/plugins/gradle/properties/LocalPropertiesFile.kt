// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.properties.base.BasePropertiesFile
import org.jetbrains.plugins.gradle.properties.LocalProperties.EMPTY
import org.jetbrains.plugins.gradle.properties.models.Property
import java.nio.file.Path

const val LOCAL_JAVA_HOME_PROPERTY = "jdk.dir"
const val LOCAL_PROPERTIES_FILE_NAME = "local.properties"

object LocalPropertiesFile : BasePropertiesFile<LocalProperties>() {

  override val propertiesFileName = LOCAL_PROPERTIES_FILE_NAME

  override fun getProperties(project: Project, externalProjectPath: Path): LocalProperties {
    val propertiesPath = getLocalProjectPropertiesPath(externalProjectPath)
    return loadLocalProperties(propertiesPath)
  }

  private fun loadLocalProperties(propertiesPath: Path): LocalProperties {
    val properties = loadProperties(propertiesPath) ?: return EMPTY
    val javaHome = properties.getProperty(LOCAL_JAVA_HOME_PROPERTY)
    val javaHomeProperty = javaHome?.let { Property(it, propertiesPath.toString()) }
    return LocalPropertiesImpl(javaHomeProperty)
  }

  private fun getLocalProjectPropertiesPath(externalProjectPath: Path) =
    externalProjectPath.resolve(propertiesFileName).toAbsolutePath().normalize()
}