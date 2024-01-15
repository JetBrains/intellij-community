// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project.wizard

import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.wizard.MavenizedNewProjectWizardData
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask

interface GradleNewProjectWizardData : MavenizedNewProjectWizardData<ProjectData> {

  val sdkProperty: GraphProperty<Sdk?>

  var sdk: Sdk?

  val sdkDownloadTaskProperty: ObservableMutableProperty<SdkDownloadTask?>

  var sdkDownloadTask: SdkDownloadTask?

  val gradleDslProperty: GraphProperty<GradleNewProjectWizardStep.GradleDsl>

  var gradleDsl: GradleNewProjectWizardStep.GradleDsl
}