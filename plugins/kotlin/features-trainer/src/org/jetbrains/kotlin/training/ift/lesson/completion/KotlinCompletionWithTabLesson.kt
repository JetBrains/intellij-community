// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.training.ift.lesson.completion

import training.dsl.LessonSample
import training.dsl.parseLessonSample
import training.learn.lesson.general.CompletionWithTabLesson

class KotlinCompletionWithTabLesson :
    CompletionWithTabLesson("DO_NOTHING_ON_CLOSE") {
    override val sample: LessonSample = parseLessonSample("""import javax.swing.*
        
fun main() {
    val frame = JFrame("FrameDemo")
    frame.setSize(175, 100)

    frame.setDefaultCloseOperation(WindowConstants.<caret>DISPOSE_ON_CLOSE)
    frame.isVisible = true
}""".trimIndent())
}