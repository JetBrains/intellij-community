// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn

import org.jetbrains.annotations.Nls
import training.lang.LangSupport
import training.learn.interfaces.Lesson
import training.learn.interfaces.LessonType

class LearningModule(@Nls name: String,
                     @Nls description: String,
                     primaryLanguage: LangSupport,
                     moduleType: LessonType,
                     private val sampleFileName: String? = null,
                     initLessons: (LearningModule) -> List<Lesson>): LearningModuleBase(name, description, primaryLanguage, moduleType) {

  override val sanitizedName: String
    get() = sampleFileName ?: error("Module $name for ${primaryLanguage.primaryLanguage} does not define its default name for samples.")

  override val lessons: List<Lesson> = initLessons(this)
}
