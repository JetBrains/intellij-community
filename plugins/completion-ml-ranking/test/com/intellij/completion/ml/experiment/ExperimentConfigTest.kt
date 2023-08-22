// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.experiment

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.LightIdeaTestCase
import org.assertj.core.api.Assertions

class ExperimentConfigTest : LightIdeaTestCase() {

  fun `test experiment info is correct`() {
    val experimentConfig = ClientExperimentStatus.loadExperimentInfo()

    if (!ApplicationManager.getApplication().isEAP) {
      Assertions.assertThat(experimentConfig).isEqualTo(ExperimentConfig.disabledExperiment())
      return
    }

    Assertions.assertThat(experimentConfig).isNotEqualTo(ExperimentConfig.disabledExperiment())
    Assertions.assertThat(experimentConfig.version).isNotNull()
    Assertions.assertThat(experimentConfig.groups.size).isNotEqualTo(0)
    for (group in experimentConfig.groups) {
      Assertions.assertThat(group.number).isNotNull()
    }
    for (language in experimentConfig.languages) {
      Assertions.assertThat(language.experimentBucketsCount).isNotNull()
      Assertions.assertThat(language.id).isNotNull()
      Assertions.assertThat(language.includeGroups.size).isNotEqualTo(0)
    }
  }
}