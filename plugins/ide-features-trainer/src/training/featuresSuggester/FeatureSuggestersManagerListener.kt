// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.featuresSuggester

import com.intellij.util.messages.Topic
import java.util.*

interface FeatureSuggestersManagerListener : EventListener {

  companion object {
    val TOPIC = Topic(
      "FeatureSuggester events",
      FeatureSuggestersManagerListener::class.java,
      Topic.BroadcastDirection.TO_CHILDREN
    )
  }

  /**
   * This method is called after suggestion is shown
   */
  fun featureFound(suggestion: PopupSuggestion) {
  }
}
