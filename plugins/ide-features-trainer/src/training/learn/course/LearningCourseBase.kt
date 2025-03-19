// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.course

import training.dsl.LessonSample
import training.dsl.parseLessonSample
import training.lang.LangManager
import training.lang.LangSupport
import training.util.DataLoader
import java.util.Locale

abstract class LearningCourseBase(val lang: String) : LearningCourse {
  val langSupport: LangSupport by lazy { LangManager.getInstance().getLangSupportById(lang) ?: error("No language with id $lang") }

  fun loadSample(path: String): LessonSample {
    val content = DataLoader.getResourceAsStream("modules/${lang.lowercase(Locale.getDefault())}/$path", javaClass.classLoader)
      .readBytes().toString(Charsets.UTF_8)
    return parseLessonSample(content)
  }
}