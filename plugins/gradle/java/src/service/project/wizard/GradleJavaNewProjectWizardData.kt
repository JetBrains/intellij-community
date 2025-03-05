// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project.wizard

import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.util.Key

interface GradleJavaNewProjectWizardData : GradleNewProjectWizardData {

  val addSampleCodeProperty: GraphProperty<Boolean>

  var addSampleCode: Boolean

  @Deprecated("Use addSampleCodeProperty instead")
  val generateOnboardingTipsProperty: ObservableMutableProperty<Boolean>
    get() = addSampleCodeProperty

  @Deprecated("Use addSampleCode instead")
  val generateOnboardingTips: Boolean
    get() = addSampleCode

  companion object {

    val KEY = Key.create<GradleJavaNewProjectWizardData>(GradleJavaNewProjectWizardData::class.java.name)

    @JvmStatic
    val NewProjectWizardStep.javaGradleData: GradleJavaNewProjectWizardData?
      get() = data.getUserData(KEY)
  }
}