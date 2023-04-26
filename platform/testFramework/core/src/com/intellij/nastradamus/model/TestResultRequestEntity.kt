// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.nastradamus.model

import com.fasterxml.jackson.annotation.JsonProperty

data class TestResultRequestEntity(
  @JsonProperty("build_info")
  val buildInfo: BuildInfo,

  @JsonProperty("test_run_results")
  val testRunResults: List<TestResultEntity>
)
