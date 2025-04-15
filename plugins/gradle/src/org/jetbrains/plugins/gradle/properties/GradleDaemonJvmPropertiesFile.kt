// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import org.jetbrains.plugins.gradle.properties.models.getStringProperty
import org.jetbrains.plugins.gradle.util.GradleConstants.GRADLE_DAEMON_JVM_PROPERTIES_FILE_NAME
import org.jetbrains.plugins.gradle.util.GradleConstants.GRADLE_DIR_NAME
import org.jetbrains.plugins.gradle.util.GradleUtil
import java.nio.file.Path
import java.nio.file.Paths

@Deprecated("Use GradleConstants#GRADLE_DAEMON_JVM_PROPERTIES_FILE_NAME instead")
const val GRADLE_DAEMON_JVM_PROPERTIES_FILE_NAME: String = GRADLE_DAEMON_JVM_PROPERTIES_FILE_NAME

const val GRADLE_DAEMON_JVM_VERSION_PROPERTY: String = "toolchainVersion"
const val GRADLE_DAEMON_JVM_VENDOR_PROPERTY: String = "toolchainVendor"

@Deprecated("Use GradleConstants#GRADLE_DIR_NAME instead")
const val GRADLE_FOLDER: String = GRADLE_DIR_NAME

object GradleDaemonJvmPropertiesFile {

  @JvmStatic
  fun getProperties(externalProjectPath: Path): GradleDaemonJvmProperties {
    val propertiesPath = getPropertyPath(externalProjectPath)
    return loadGradleDaemonJvmProperties(propertiesPath) ?: EMPTY
  }

  @JvmStatic
  fun getPropertyPath(externalProjectPath: Path): Path {
    return externalProjectPath.resolve(Paths.get(GRADLE_DIR_NAME, GRADLE_DAEMON_JVM_PROPERTIES_FILE_NAME))
      .toAbsolutePath().normalize()
  }

  private fun loadGradleDaemonJvmProperties(propertiesPath: Path): GradleDaemonJvmProperties? {
    val properties = GradleUtil.readGradleProperties(propertiesPath) ?: return null
    return GradleDaemonJvmPropertiesImpl(
      version = properties.getStringProperty(GRADLE_DAEMON_JVM_VERSION_PROPERTY, propertiesPath),
      vendor = properties.getStringProperty(GRADLE_DAEMON_JVM_VENDOR_PROPERTY, propertiesPath)
    )
  }

  private val EMPTY = GradleDaemonJvmPropertiesImpl(
    version = null,
    vendor = null
  )
}