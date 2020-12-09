// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.interfaces

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import training.lang.LangSupport
import training.learn.LearnBundle

interface Module {

  val classLoader: ClassLoader
    get() = javaClass.classLoader

  val lessons: List<Lesson>

  val sanitizedName: @NlsSafe String

  val name: String

  val primaryLanguage: LangSupport

  /** It is lessons default type */
  val moduleType: LessonType

  val description: String?

  fun giveNotPassedLesson(): Lesson?

  fun giveNotPassedAndNotOpenedLesson(): Lesson?

  fun hasNotPassedLesson(): Boolean

  @Nls
  fun calcProgress(): String? {
    val total = lessons.size
    var done = 0
    for (lesson in lessons) {
      if (lesson.passed) done++
    }
    return if (done == total)
      LearnBundle.message("learn.module.progress.completed")
    else
      LearnBundle.message("learn.module.progress", done, total)
  }

}