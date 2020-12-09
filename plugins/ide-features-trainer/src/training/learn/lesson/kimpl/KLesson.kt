// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.kimpl

import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import training.learn.interfaces.Lesson
import training.learn.interfaces.Module
import training.learn.lesson.LessonListener

abstract class KLesson(@NonNls override val id: String,
                       @Nls override val name: String,
                       override var module: Module,
                       override val lang: String) : Lesson {

  abstract val lessonContent: LessonContext.() -> Unit

  override val lessonListeners: MutableList<LessonListener> = mutableListOf()
}
