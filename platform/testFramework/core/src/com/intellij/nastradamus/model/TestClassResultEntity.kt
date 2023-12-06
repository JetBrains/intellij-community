// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.nastradamus.model

import com.fasterxml.jackson.annotation.JsonProperty

data class TestClassResultEntity(
  @JsonProperty("full_name")
  val fullName: String,

  @JsonProperty("duration_ms")
  val durationMs: Long,

  @JsonProperty("test_results")
  val testResults: Set<TestResultEntity>,

  @JsonProperty("bucket_id")
  val bucketId: Int,

  // total buckets number for build type
  @JsonProperty("buckets_number")
  val bucketsNumber: Int
)
