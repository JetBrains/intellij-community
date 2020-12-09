// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn

import org.jetbrains.annotations.Nls
import training.lang.LangSupport
import training.learn.interfaces.Lesson
import training.learn.interfaces.LessonType
import training.learn.interfaces.Module

abstract class LearningModuleBase(@Nls override val name: String,
                              @Nls override val description: String,
                              override val primaryLanguage: LangSupport,
                              override val moduleType: LessonType) : Module {

  override fun toString(): String {
    return "($name for $primaryLanguage)"
  }

  override fun giveNotPassedLesson(): Lesson? {
    return lessons.firstOrNull { !it.passed }
  }

  override fun giveNotPassedAndNotOpenedLesson(): Lesson? {
    return lessons.firstOrNull { !it.passed }
  }

  override fun hasNotPassedLesson(): Boolean {
    return lessons.any { !it.passed }
  }
}
