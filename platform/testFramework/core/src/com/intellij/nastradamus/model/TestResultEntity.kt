// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.nastradamus.model

enum class TestStatus(status: String) {
  SUCCESS("success"),
  FAILED("failed"),
  IGNORED("ignored"),
  UNKNOWN("unknown");

  val status: String = status.lowercase()

  companion object {
    fun fromString(input: String): TestStatus {
      return valueOf(input.lowercase())
    }
  }
}

data class TestResultEntity(val name: String, val status: TestStatus)
