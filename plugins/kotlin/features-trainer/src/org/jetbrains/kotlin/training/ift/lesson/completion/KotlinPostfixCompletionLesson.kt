// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.training.ift.lesson.completion

import com.intellij.java.ift.JavaLessonsBundle
import training.dsl.LearningDslBase
import training.dsl.LessonSample
import training.dsl.parseLessonSample
import training.learn.lesson.general.completion.PostfixCompletionLesson

class KotlinPostfixCompletionLesson : PostfixCompletionLesson() {
    override val sample: LessonSample = parseLessonSample(
        """
    fun demonstrate(showTimes: Int) {
        showTimes<caret>
    }
  """.trimIndent()
    )

    override val result: String = parseLessonSample(
        """
    fun demonstrate(showTimes: Int) {
        for (i in 0 until showTimes) {
            
        }
    }
  """.trimIndent()
    ).text


    override val completionSuffix: String = ".fo"
    override val completionItem: String = "fori"

    override fun LearningDslBase.getTypeTaskText(): String {
        return JavaLessonsBundle.message("java.postfix.completion.type", code(completionSuffix))
    }

    override fun LearningDslBase.getCompleteTaskText(): String {
        return JavaLessonsBundle.message("java.postfix.completion.complete", code(completionItem), action("EditorChooseLookupItem"))
    }
}