// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.training.ift

import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.training.ift.lesson.basic.KotlinBasicCompletionLesson
import org.jetbrains.kotlin.training.ift.lesson.basic.KotlinContextActionsLesson
import org.jetbrains.kotlin.training.ift.lesson.basic.KotlinSelectLesson
import org.jetbrains.kotlin.training.ift.lesson.navigation.KotlinFileStructureLesson
import org.jetbrains.kotlin.training.ift.lesson.navigation.KotlinSearchEverywhereLesson
import training.dsl.LessonUtil
import training.learn.LessonsBundle
import training.learn.course.LearningCourseBase
import training.learn.course.LearningModule
import training.learn.course.LessonType
import training.learn.lesson.general.GotoActionLesson

class KotlinLearningCourse : LearningCourseBase(KotlinLanguage.INSTANCE.id) {
    override fun modules() = stableModules()

    private fun stableModules() = listOf(
        LearningModule(
            id = "Kotlin.Essential",
            name = LessonsBundle.message("essential.module.name"),
            description = LessonsBundle.message("essential.module.description", LessonUtil.productName),
            primaryLanguage = langSupport,
            moduleType = LessonType.SINGLE_EDITOR  // todo: change to SCRATCH when KTIJ-20742 will be resolved
        ) {
            fun ls(sampleName: String) = loadSample("EditorBasics/$sampleName")
            listOf(
                KotlinContextActionsLesson(),
                GotoActionLesson(ls("Actions.kt.sample"), firstLesson = false),
                KotlinSearchEverywhereLesson(),
                KotlinBasicCompletionLesson(),
            )
        },
        LearningModule(
            id = "Kotlin.EditorBasics",
            name = LessonsBundle.message("editor.basics.module.name"),
            description = LessonsBundle.message("editor.basics.module.description"),
            primaryLanguage = langSupport,
            moduleType = LessonType.SCRATCH
        ) {
            listOf(KotlinSelectLesson())
        },
        LearningModule(
            id = "Kotlin.CodeCompletion",
            name = LessonsBundle.message("code.completion.module.name"),
            description = LessonsBundle.message("code.completion.module.description"),
            primaryLanguage = langSupport,
            moduleType = LessonType.SINGLE_EDITOR  // todo: change to SCRATCH when KTIJ-20742 will be resolved
        ) {
            listOf(KotlinBasicCompletionLesson())
        },
        LearningModule(
            id = "Kotlin.Navigation",
            name = LessonsBundle.message("navigation.module.name"),
            description = LessonsBundle.message("navigation.module.description"),
            primaryLanguage = langSupport,
            moduleType = LessonType.PROJECT
        ) {
            listOf(
                KotlinSearchEverywhereLesson(),
                KotlinFileStructureLesson(),
            )
        }
    )
}