// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.util

import com.intellij.ui.dsl.builder.Panel
import kotlinx.serialization.json.JsonObjectBuilder
import org.jetbrains.annotations.Nls

abstract class OnboardingFeedbackData(val reportTitle: String, // It is ZenDesk title, so should not be translated
                                      val lessonEndInfo: LessonEndInfo,
                                      ) {
  abstract val feedbackReportId: String

  /** This value will be add to onboarding feedback format version */
  abstract val additionalFeedbackFormatVersion: Int

  abstract val addAdditionalSystemData: JsonObjectBuilder.() -> Unit

  abstract val addRowsForUserAgreement: Panel.() -> Unit

  abstract val possibleTechnicalIssues: Map<String, @Nls String>

  abstract fun feedbackHasBeenProposed()
}