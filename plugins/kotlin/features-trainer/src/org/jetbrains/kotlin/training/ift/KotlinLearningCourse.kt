// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.training.ift

import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.training.ift.lesson.basic.KotlinSelectLesson
import training.learn.LessonsBundle
import training.learn.course.LearningCourseBase
import training.learn.course.LearningModule
import training.learn.course.LessonType

class KotlinLearningCourse : LearningCourseBase(KotlinLanguage.INSTANCE.id) {
    override fun modules() = stableModules()

    private fun stableModules() = listOf(
        LearningModule(
            name = LessonsBundle.message("editor.basics.module.name"),
            description = LessonsBundle.message("editor.basics.module.description"),
            primaryLanguage = langSupport,
            moduleType = LessonType.SCRATCH
        ) {
            listOf(KotlinSelectLesson())
        }
    )
}