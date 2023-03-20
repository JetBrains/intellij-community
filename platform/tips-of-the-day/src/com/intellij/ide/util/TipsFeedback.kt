// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util

import com.intellij.feedback.common.FeedbackRequestData
import com.intellij.feedback.common.FeedbackRequestType
import com.intellij.feedback.common.submitFeedback
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.xmlb.annotations.XMap
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Service
@State(name = "TipsFeedback", storages = [Storage(value = "tips-feedback.xml", roamingType = RoamingType.DISABLED)])
class TipsFeedback : SimplePersistentStateComponent<TipsFeedback.State>(State()) {
  fun getLikenessState(tipId: String): Boolean? {
    return state.tipToLikeness[tipId]
  }

  fun setLikenessState(tipId: String, likenessState: Boolean?) {
    if (likenessState != null) {
      state.tipToLikeness[tipId] = likenessState
      state.intIncrementModificationCount()
    }
    else state.tipToLikeness.remove(tipId)?.also {
      state.intIncrementModificationCount()
    }
  }

  class State : BaseState() {
    @get:XMap
    val tipToLikeness by linkedMap<String, Boolean>()
  }

  fun scheduleFeedbackSending(tipId: String, likenessState: Boolean) {
    val dataJsonObject = buildJsonObject {
      put("format_version", FEEDBACK_FORMAT_VERSION)
      put("tip_id", tipId)
      put("like", likenessState)
      put("ide_name", ApplicationNamesInfo.getInstance().fullProductName)
      put("ide_build", ApplicationInfo.getInstance().build.asStringWithoutProductCode())
    }
    val feedbackData = FeedbackRequestData(FEEDBACK_REPORT_ID, dataJsonObject)
    submitFeedback(project = null,
                   feedbackData = feedbackData,
                   onDone = {},
                   onError = {},
                   feedbackRequestType = getFeedbackRequestType(),
                   thanksNotification = null)
  }

  private fun getFeedbackRequestType(): FeedbackRequestType {
    return when (Registry.stringValue("tips.of.the.day.feedback")) {
      "production" -> FeedbackRequestType.PRODUCTION_REQUEST
      "staging" -> FeedbackRequestType.TEST_REQUEST
      else -> FeedbackRequestType.NO_REQUEST
    }
  }

  companion object {
    private const val FEEDBACK_REPORT_ID = "tips_of_the_day"
    private const val FEEDBACK_FORMAT_VERSION = 1

    @JvmStatic
    fun getInstance(): TipsFeedback = service()
  }
}