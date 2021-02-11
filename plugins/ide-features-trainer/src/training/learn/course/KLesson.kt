// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.course

import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import training.dsl.LessonContext
import training.learn.lesson.LessonListener
import training.util.findLanguageByID

abstract class KLesson(@NonNls override val id: String,
                       @Nls override val name: String,
                       override val lang: String) : Lesson {
  override lateinit var module: Module

  abstract val lessonContent: LessonContext.() -> Unit

  override val lessonListeners: MutableList<LessonListener> = mutableListOf()

  override val fileName: String
    get() {
      return module.sanitizedName + "." + findLanguageByID(lang)!!.associatedFileType!!.defaultExtension
    }

  override val lessonType: LessonType get() = module.moduleType
}
