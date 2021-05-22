// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.course

import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import training.dsl.LessonContext

abstract class KLesson(@NonNls id: String, @Nls name: String) : Lesson(id, name) {
  abstract val lessonContent: LessonContext.() -> Unit

  override lateinit var module: IftModule
    internal set
}
