// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn

import org.jetbrains.annotations.Nls
import training.lang.LangSupport
import training.learn.interfaces.LessonType
import training.learn.lesson.kimpl.KLesson

class LearningModule(@Nls name: String,
                     @Nls description: String,
                     primaryLanguage: LangSupport,
                     moduleType: LessonType,
                     private val sampleFileName: String? = null,
                     initLessons: () -> List<KLesson>): LearningModuleBase(name, description, primaryLanguage, moduleType, initLessons) {

  override val sanitizedName: String
    get() = sampleFileName ?: error("Module $name for ${primaryLanguage.primaryLanguage} does not define its default name for samples.")
}
