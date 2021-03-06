// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.course

import org.jetbrains.annotations.Nls
import training.lang.LangSupport

class LearningModule(@Nls name: String,
                     @Nls description: String,
                     primaryLanguage: LangSupport,
                     moduleType: LessonType,
                     private val sampleFileName: String? = null,
                     initLessons: () -> List<KLesson>): IftModule(name, description, primaryLanguage, moduleType, initLessons) {

  override val sanitizedName: String
    get() = sampleFileName ?: error("Module $name for ${primaryLanguage.primaryLanguage} does not define its default name for samples.")
}
