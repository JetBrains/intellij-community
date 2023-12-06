// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.nastradamus.model

import com.fasterxml.jackson.annotation.JsonProperty

enum class TestStatus(status: String) {
  SUCCESS("SUCCESS"),
  FAILURE("FAILURE"),
  IGNORED("IGNORED"),
  ABORTED("ABORTED"),
  UNKNOWN("UNKNOWN");

  val status: String = status.lowercase()

  companion object {
    fun fromString(input: String): TestStatus {
      return values().singleOrNull { input.lowercase() == it.status } ?: UNKNOWN
    }
  }
}

data class TestResultEntity(
  val name: String,
  val status: TestStatus,

  @JsonProperty("run_order")
  val runOrder: Int,

  @JsonProperty("duration_ms")
  val durationMs: Long,

  @JsonProperty("currently_investigated")
  val currentlyInvestigated: Boolean,

  @JsonProperty("is_muted")
  val isMuted: Boolean
)
