// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project.wizard

import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.wizard.MavenizedNewProjectWizardData
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.projectRoots.Sdk

interface GradleNewProjectWizardData : MavenizedNewProjectWizardData<ProjectData> {
  val sdkProperty: GraphProperty<Sdk?>
  val useKotlinDslProperty: GraphProperty<Boolean>

  var sdk: Sdk?
  var useKotlinDsl: Boolean
}