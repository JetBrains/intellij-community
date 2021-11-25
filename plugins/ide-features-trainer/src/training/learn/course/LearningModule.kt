// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.course

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import training.lang.LangSupport

class LearningModule(@NonNls id: String,
                     @Nls name: String,
                     @Nls description: String,
                     primaryLanguage: LangSupport,
                     moduleType: LessonType,
                     private val sampleFileName: String? = null,
                     initLessons: () -> List<KLesson>) : IftModule(id, name, description, primaryLanguage, moduleType, initLessons) {

  override val sanitizedName: String
    get() = sampleFileName ?: error("Module $name for ${primaryLanguage?.primaryLanguage} does not define its default name for samples.")

  override fun preferredLearnWindowAnchor(project: Project) = primaryLanguage!!.getToolWindowAnchor()
}
