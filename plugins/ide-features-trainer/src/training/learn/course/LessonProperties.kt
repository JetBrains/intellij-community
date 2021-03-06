// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.course

data class LessonProperties(
  val canStartInDumbMode: Boolean = false,
  val openFileAtStart: Boolean = true,
  val showLearnToolwindowAtStart: Boolean = true,
)