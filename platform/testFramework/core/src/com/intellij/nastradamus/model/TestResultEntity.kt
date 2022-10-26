// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.nastradamus.model

import com.fasterxml.jackson.annotation.JsonProperty

enum class TestStatus(status: String) {
  SUCCESS("success"),
  FAILED("failed"),
  IGNORED("ignored"),
  UNKNOWN("unknown");

  val status: String = status.lowercase()

  companion object {
    fun fromString(input: String): TestStatus {
      return values().single { input.lowercase() == it.status }
    }
  }
}

data class TestResultEntity(
  val name: String,
  val status: TestStatus,

  @JsonProperty("run_order")
  val runOrder: Int,
  val duration: Long // in milliseconds
)
