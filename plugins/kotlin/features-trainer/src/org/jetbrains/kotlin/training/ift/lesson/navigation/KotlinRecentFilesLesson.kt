// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.training.ift.lesson.navigation

import training.dsl.LessonContext
import training.dsl.LessonUtil
import training.learn.LessonsBundle
import training.learn.lesson.general.navigation.RecentFilesLesson

class KotlinRecentFilesLesson : RecentFilesLesson() {
    override val sampleFilePath: String = "src/RecentFilesDemo.kt"

    override val transitionMethodName: String = "println"
    override val transitionFileName: String = "Console"
    override val stringForRecentFilesSearch: String = "print"

    override fun LessonContext.setInitialPosition(): Unit = caret("println")

    override val helpLinks: Map<String, String>
        get() = mapOf(
            Pair(
                LessonsBundle.message("recent.files.locations.help.link"),
                LessonUtil.getHelpLink("idea", "discover-intellij-idea.html#recent-files")
            ),
        )
}