// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.telemetry

import io.opentelemetry.api.common.AttributeKey

object VcsTelemetrySpanAttribute {
  val IS_INITIAL_HISTORY_COMPUTING: AttributeKey<Boolean> = AttributeKey.booleanKey("isInitial")
  val TYPE_HISTORY_COMPUTING: AttributeKey<String> = AttributeKey.stringKey("type")
  val HISTORY_COMPUTING_VCS_NAME: AttributeKey<String> = AttributeKey.stringKey("vcsName")
}
