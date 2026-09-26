// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.config.wizard

import com.intellij.ide.wizard.BuildSystemNewProjectWizardData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.roots.ui.distribution.DistributionInfo
import com.intellij.openapi.util.Key

interface BuildSystemGroovyNewProjectWizardData: BuildSystemNewProjectWizardData {

  val groovySdkProperty : GraphProperty<DistributionInfo?>

  var groovySdk : DistributionInfo?

  companion object {
    val KEY: Key<BuildSystemGroovyNewProjectWizardData> = Key.create(BuildSystemGroovyNewProjectWizardData::class.java.name)

    @JvmStatic
    val NewProjectWizardStep.groovyBuildSystemData: BuildSystemGroovyNewProjectWizardData?
      get() = data.getUserData(KEY)
  }
}