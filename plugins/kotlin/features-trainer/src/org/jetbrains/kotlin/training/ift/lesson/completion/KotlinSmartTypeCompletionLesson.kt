// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.training.ift.lesson.completion

import com.intellij.java.ift.lesson.completion.SmartTypeCompletionLessonBase
import training.dsl.LessonContext
import training.dsl.LessonSample
import training.dsl.parseLessonSample

class KotlinSmartTypeCompletionLesson : SmartTypeCompletionLessonBase() {

    override val sample: LessonSample = parseLessonSample("""
    object TestObject
    
    fun smartCompletionDemo(): TestObject {
        val iterations = 5
        repeat(<caret>) {
            println("Hello")
        }
    
        return 
    }
""".trimIndent())

    override val firstCompletionItem: String = "iterations"
    override val firstCompletionCheck: String = "repeat(iterations)"

    override val secondCompletionItem: String = "TestObject"
    override val secondCompletionCheck: String = "return TestObject"

    override fun LessonContext.setCaretForSecondItem() {
        caret(9, 12)
    }
}