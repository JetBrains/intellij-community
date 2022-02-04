// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.config.wizard

import com.intellij.ide.wizard.BuildSystemNewProjectWizardData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.util.Key

interface BuildSystemGroovyNewProjectWizardData: BuildSystemNewProjectWizardData {

  val groovySdkProperty : GraphProperty<String?>

  var groovySdk : String?

  companion object {
    @JvmStatic val KEY = Key.create<BuildSystemGroovyNewProjectWizardData>(BuildSystemGroovyNewProjectWizardData::class.java.name)

    @JvmStatic val NewProjectWizardStep.buildSystemData get() = data.getUserData(KEY)!!

    @JvmStatic val NewProjectWizardStep.buildSystemProperty get() = buildSystemData.buildSystemProperty
    @JvmStatic var NewProjectWizardStep.buildSystem get() = buildSystemData.buildSystem; set(it) { buildSystemData.buildSystem = it }
  }
}