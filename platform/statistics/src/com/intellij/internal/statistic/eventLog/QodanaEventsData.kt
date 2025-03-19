// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.util.PlatformUtils
import java.util.regex.Pattern

private const val QODANA_PROJECT_ID_PATTERN = "([-0-9A-Fa-f]{32,64})"
private const val QODANA_ORGANIZATION_ID_PATTERN = "([0-9A-Za-z]{2,16})"

internal class QodanaEventsData(val projectId:String?, val organizationId:String?)

internal fun calcQodanaEventsData(): QodanaEventsData {
  if (!PlatformUtils.isQodana()) return QodanaEventsData(null, null)
  return QodanaEventsData(
    calcQodanaProjectId(),
    calcQodanaOrganisationsId()
  )
}

private fun calcQodanaProjectId(): String? {
  val env = System.getenv("QODANA_PROJECT_ID_HASH") ?: return null
  if (env.isEmpty()) return null
  return if (Pattern.compile(QODANA_PROJECT_ID_PATTERN).matcher(env).matches()) env else ValidationResultType.REJECTED.description
}

private fun calcQodanaOrganisationsId(): String? {
  val env = System.getenv("QODANA_ORGANISATION_ID_HASH") ?: return null
  if (env.isEmpty()) return null
  return if (Pattern.compile(QODANA_ORGANIZATION_ID_PATTERN).matcher(env).matches()) env else ValidationResultType.REJECTED.description
}