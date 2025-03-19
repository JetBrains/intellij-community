// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GradleJvmComboBoxUtil")
@file:ApiStatus.Internal

package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.JAVA_HOME
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.getJavaHome
import com.intellij.openapi.externalSystem.service.ui.addJdkReferenceItem
import com.intellij.openapi.externalSystem.service.ui.resolveJdkReference
import com.intellij.openapi.externalSystem.service.ui.setSelectedJdkReference
import com.intellij.openapi.roots.ui.configuration.SdkComboBox
import com.intellij.openapi.roots.ui.configuration.SdkListItem
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.properties.GRADLE_JAVA_HOME_PROPERTY
import org.jetbrains.plugins.gradle.properties.GradleLocalPropertiesFile
import org.jetbrains.plugins.gradle.properties.GradlePropertiesFile


fun SdkComboBox.getSelectedGradleJvmReference(sdkLookupProvider: SdkLookupProvider): String? {
  return sdkLookupProvider.resolveGradleJvmReference(selectedItem)
}

fun SdkComboBox.setSelectedGradleJvmReference(sdkLookupProvider: SdkLookupProvider, externalProjectPath: String?, jdkReference: String?) {
  when (jdkReference) {
    USE_GRADLE_JAVA_HOME -> {
      val javaHome = GradlePropertiesFile.getJavaHome(model.project, externalProjectPath)
      selectedItem = addJdkReferenceItem(GRADLE_JAVA_HOME_PROPERTY, javaHome)
    }
    USE_GRADLE_LOCAL_JAVA_HOME -> {
      val javaHome = GradleLocalPropertiesFile.getJavaHome(externalProjectPath)
      selectedItem = addJdkReferenceItem(GRADLE_LOCAL_JAVA_HOME, javaHome)
    }
    else -> setSelectedJdkReference(sdkLookupProvider, jdkReference)
  }
}

fun SdkComboBox.addUsefulGradleJvmReferences(externalProjectPath: String?) {
  addGradleJavaHomeReferenceItem(externalProjectPath)
  addGradleLocalJavaHomeReferenceItem(externalProjectPath)
  addJavaHomeReferenceItem()
}

fun SdkLookupProvider.resolveGradleJvmReference(item: SdkListItem?): String? {
  return when (item) {
    is SdkListItem.SdkReferenceItem -> when (item.name) {
      GRADLE_JAVA_HOME_PROPERTY -> USE_GRADLE_JAVA_HOME
      GRADLE_LOCAL_JAVA_HOME -> USE_GRADLE_LOCAL_JAVA_HOME
      else -> resolveJdkReference(item)
    }
    else -> resolveJdkReference(item)
  }
}

private fun SdkComboBox.addGradleJavaHomeReferenceItem(externalProjectPath: String?) {
  if (externalProjectPath == null) return
  val javaHome = GradlePropertiesFile.getJavaHome(model.project, externalProjectPath) ?: return
  addJdkReferenceItem(GRADLE_JAVA_HOME_PROPERTY, javaHome)
}

private fun SdkComboBox.addGradleLocalJavaHomeReferenceItem(externalProjectPath: String?) {
  if (externalProjectPath == null) return
  val javaHome = GradleLocalPropertiesFile.getJavaHome(externalProjectPath) ?: return
  addJdkReferenceItem(GRADLE_LOCAL_JAVA_HOME, javaHome)
}

private fun SdkComboBox.addJavaHomeReferenceItem() {
  val javaHome = getJavaHome() ?: return
  addJdkReferenceItem(JAVA_HOME, javaHome)
}