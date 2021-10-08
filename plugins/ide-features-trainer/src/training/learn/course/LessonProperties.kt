// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.course

data class LessonProperties(
  val canStartInDumbMode: Boolean = false,
  val openFileAtStart: Boolean = true,

  /** The new lessons can specify its release build version (like 212 or 212.4020) to get into promotion notification */
  val availableSince: String? = null
)