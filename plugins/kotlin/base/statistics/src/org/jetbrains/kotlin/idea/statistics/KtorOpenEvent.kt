// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.statistics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class KtorOpenEvent(
  @SerialName("spec") val spec: Spec,
  @SerialName("timestamp") val timestamp: String,
  @SerialName("uid") val uid: String? = null
) {
  @Serializable
  data class Spec(
    @SerialName("template_id") val template: String,
    @SerialName("parameters") val parameters: Parameters
  )

  @Serializable
  data class Parameters(
    @SerialName("utm_source") val utmSource: String,
    @SerialName("utm_medium") val utmMedium: String,
    @SerialName("utm_campaign") val utmCampaign: String
  )
}
