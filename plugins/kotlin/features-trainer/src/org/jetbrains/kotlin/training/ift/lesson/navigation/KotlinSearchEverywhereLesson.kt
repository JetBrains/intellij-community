// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.training.ift.lesson.navigation

import training.dsl.LessonContext
import training.learn.LessonsBundle
import training.learn.lesson.general.navigation.SearchEverywhereLesson

class KotlinSearchEverywhereLesson : SearchEverywhereLesson() {
    override val existedFile = "src/RecentFilesDemo.kt"
    override val resultFileName: String = "QuadraticEquationsSolver.kt"

    override fun LessonContext.epilogue() {
        text(LessonsBundle.message("search.everywhere.navigation.promotion", strong(LessonsBundle.message("navigation.module.name"))))
    }
}