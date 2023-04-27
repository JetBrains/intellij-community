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
  val duration: Long, // in milliseconds

  @JsonProperty("build_status_message")
  val buildStatusMessage: String,

  @JsonProperty("is_muted")
  val isMuted: Boolean,

  @JsonProperty("bucket_id")
  val bucketId: Int,

  // total buckets number for build type
  @JsonProperty("buckets_number")
  val bucketsNumber: Int
)
