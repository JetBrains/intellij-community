// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.course

import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import training.dsl.LessonContext
import training.lang.LangManager
import training.lang.LangSupport

abstract class KLesson(@NonNls id: String, @Nls name: String) : Lesson(id, name) {
  protected abstract val lessonContent: LessonContext.() -> Unit

  override lateinit var module: IftModule
    internal set

  val fullLessonContent: LessonContext.() -> Unit get() {
    val languageSupport: LangSupport = LangManager.getInstance().getLangSupport() ?: return lessonContent
    if (languageId == languageSupport.primaryLanguage) {
      return {
        languageSupport.commonCheckContent.invoke(this, this@KLesson)
        lessonContent()
      }
    }
    return lessonContent
  }
}
