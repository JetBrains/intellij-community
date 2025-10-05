// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project.wizard

import com.intellij.ide.projectWizard.ProjectWizardJdkIntent
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.wizard.MavenizedNewProjectWizardData
import com.intellij.openapi.observable.properties.GraphProperty
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl

interface GradleNewProjectWizardData : MavenizedNewProjectWizardData<ProjectData> {

  val jdkIntentProperty: GraphProperty<ProjectWizardJdkIntent>

  var jdkIntent: ProjectWizardJdkIntent

  val gradleDslProperty: GraphProperty<GradleDsl>

  var gradleDsl: GradleDsl
}