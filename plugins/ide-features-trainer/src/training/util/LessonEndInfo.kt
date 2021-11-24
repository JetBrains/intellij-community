// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.util

import kotlinx.serialization.Serializable

@Serializable
data class LessonEndInfo(
  val lessonPassed: Boolean,
  val currentTaskIndex: Int,
  val currentVisualIndex: Int,
)
