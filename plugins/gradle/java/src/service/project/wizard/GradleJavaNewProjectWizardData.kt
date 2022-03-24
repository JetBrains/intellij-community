// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project.wizard

import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.util.Key

interface GradleJavaNewProjectWizardData : GradleNewProjectWizardData {

  val addSampleCodeProperty: GraphProperty<Boolean>
  var addSampleCode: Boolean

  companion object {
    @JvmStatic val KEY = Key.create<GradleJavaNewProjectWizardData>(GradleJavaNewProjectWizardData::class.java.name)

    @JvmStatic val NewProjectWizardStep.gradleData get() = data.getUserData(KEY)!!

    @JvmStatic val NewProjectWizardStep.sdkProperty get() = gradleData.sdkProperty
    @JvmStatic val NewProjectWizardStep.useKotlinDslProperty get() = gradleData.useKotlinDslProperty
    @JvmStatic val NewProjectWizardStep.parentProperty get() = gradleData.parentProperty
    @JvmStatic val NewProjectWizardStep.groupIdProperty get() = gradleData.groupIdProperty
    @JvmStatic val NewProjectWizardStep.artifactIdProperty get() = gradleData.artifactIdProperty
    @JvmStatic val NewProjectWizardStep.versionProperty get() = gradleData.versionProperty
    @JvmStatic val NewProjectWizardStep.addSampleCodeProperty get() = gradleData.addSampleCodeProperty
    @JvmStatic var NewProjectWizardStep.sdk get() = gradleData.sdk; set(it) { gradleData.sdk = it }
    @JvmStatic var NewProjectWizardStep.useKotlinDsl get() = gradleData.useKotlinDsl; set(it) { gradleData.useKotlinDsl = it }
    @JvmStatic var NewProjectWizardStep.parent get() = gradleData.parent; set(it) { gradleData.parent = it }
    @JvmStatic var NewProjectWizardStep.parentData get() = gradleData.parentData; set(it) { gradleData.parentData = it }
    @JvmStatic var NewProjectWizardStep.groupId get() = gradleData.groupId; set(it) { gradleData.groupId = it }
    @JvmStatic var NewProjectWizardStep.artifactId get() = gradleData.artifactId; set(it) { gradleData.artifactId = it }
    @JvmStatic var NewProjectWizardStep.version get() = gradleData.version; set(it) { gradleData.version = it }
    @JvmStatic var NewProjectWizardStep.addSampleCode get() = gradleData.addSampleCode; set(it) { gradleData.addSampleCode = it }
  }
}