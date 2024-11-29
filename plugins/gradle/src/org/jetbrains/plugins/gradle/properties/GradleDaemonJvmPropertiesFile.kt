// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import org.jetbrains.plugins.gradle.properties.models.getStringProperty
import org.jetbrains.plugins.gradle.util.GradleUtil
import java.nio.file.Path
import java.nio.file.Paths

const val GRADLE_DAEMON_JVM_PROPERTIES_FILE_NAME = "gradle-daemon-jvm.properties"
const val GRADLE_DAEMON_JVM_VERSION_PROPERTY = "toolchainVersion"
const val GRADLE_DAEMON_JVM_VENDOR_PROPERTY = "toolchainVendor"
const val GRADLE_FOLDER = "gradle"

object GradleDaemonJvmPropertiesFile {
  fun getProperties(externalProjectPath: Path): GradleDaemonJvmProperties? {
    val propertiesPath = getGradleDaemonJvmPropertiesPath(externalProjectPath)
    return loadGradleDaemonJvmProperties(propertiesPath)
  }

  private fun loadGradleDaemonJvmProperties(propertiesPath: Path): GradleDaemonJvmProperties? {
    val properties = GradleUtil.readGradleProperties(propertiesPath) ?: return null
    return GradleDaemonJvmPropertiesImpl(
      version = properties.getStringProperty(GRADLE_DAEMON_JVM_VERSION_PROPERTY, propertiesPath),
      vendor = properties.getStringProperty(GRADLE_DAEMON_JVM_VENDOR_PROPERTY, propertiesPath)
    )
  }

  private fun getGradleDaemonJvmPropertiesPath(externalProjectPath: Path): Path {
    return externalProjectPath.resolve(Paths.get(GRADLE_FOLDER, GRADLE_DAEMON_JVM_PROPERTIES_FILE_NAME)).toAbsolutePath().normalize()
  }
}