// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.course

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.ToolWindowAnchor
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import training.lang.LangSupport

abstract class IftModule(@NonNls val id: String,
                         @Nls val name: String,
                         @Nls val description: String,
                         val primaryLanguage: LangSupport?,
                         /** It is lessons default type */
                         val moduleType: LessonType,
                         initLessons: () -> List<KLesson>) {

  val lessons: List<KLesson> = initLessons()

  init {
    for (lesson in lessons) {
      @Suppress("LeakingThis")
      lesson.module = this
    }
  }

  /**
   * Relative path to file in the learning project. Will be used existed or generated the new empty file.
   * Has a second priority after [Lesson]'s `sampleFilePath`.
   */
  abstract val sampleFilePath: @NlsSafe String?

  abstract fun preferredLearnWindowAnchor(project: Project): ToolWindowAnchor

  override fun toString(): String {
    return "($name for $primaryLanguage)"
  }
}
