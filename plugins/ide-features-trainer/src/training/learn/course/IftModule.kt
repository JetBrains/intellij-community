// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.course

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import training.lang.LangSupport

abstract class IftModule(@Nls val name: String,
                         @Nls val description: String,
                         val primaryLanguage: LangSupport,
                         /** It is lessons default type */
                         val moduleType: LessonType,
                         initLessons: () -> List<KLesson>) {

  val lessons: List<KLesson> = initLessons()

  init {
    for (lesson in lessons) {
      lesson.module = this
    }
  }

  abstract val sanitizedName: @NlsSafe String

  override fun toString(): String {
    return "($name for $primaryLanguage)"
  }
}
