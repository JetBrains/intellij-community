// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.telemetry

import io.opentelemetry.api.common.AttributeKey

object VcsTelemetrySpanAttribute {
  val IS_INITIAL_HISTORY_COMPUTING: AttributeKey<Boolean> = AttributeKey.booleanKey("isInitial")
  val TYPE_HISTORY_COMPUTING: AttributeKey<String> = AttributeKey.stringKey("type")
  val HISTORY_COMPUTING_VCS_NAME: AttributeKey<String> = AttributeKey.stringKey("vcsName")
  val VCS_LOG_FILTERS_LIST: AttributeKey<String> = AttributeKey.stringKey("filters")
  val VCS_LOG_SORT_TYPE: AttributeKey<String> = AttributeKey.stringKey("sortType")
  val VCS_LOG_VCS_LIST: AttributeKey<String> = AttributeKey.stringKey("vcsList")
  val VCS_LOG_FILTERED_COMMIT_COUNT: AttributeKey<String> = AttributeKey.stringKey("filteredCommitCount")
  val VCS_LOG_REPOSITORY_COMMIT_COUNT: AttributeKey<Long> = AttributeKey.longKey("repositoryCommitCount")
  val VCS_LOG_FILTER_KIND: AttributeKey<String> = AttributeKey.stringKey("filterKind")
}
