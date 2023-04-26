// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.nastradamus.model

import com.fasterxml.jackson.annotation.JsonProperty

data class BuildInfo(
  @JsonProperty("build_id")
  val buildId: String,

  @JsonProperty("aggregator_build_id")
  val aggregatorBuildId: String,

  @JsonProperty("branch_name")
  val branchName: String,

  val os: String,

  @JsonProperty("build_type")
  val buildType: String
)
