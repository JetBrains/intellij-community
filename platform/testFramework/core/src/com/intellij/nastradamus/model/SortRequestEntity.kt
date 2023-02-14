// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.nastradamus.model

import com.fasterxml.jackson.annotation.JsonProperty

data class SortRequestEntity(
  @JsonProperty("build_info")
  val buildInfo: BuildInfo,
  val changes: List<ChangeEntity>,
  val tests: List<TestCaseEntity>
)