// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.training.ift.lesson.basic

import org.jetbrains.kotlin.idea.KotlinBundle
import training.dsl.LessonSample
import training.dsl.parseLessonSample
import training.learn.lesson.general.ContextActionsLesson

class KotlinContextActionsLesson : ContextActionsLesson() {
    override val sample: LessonSample = parseLessonSample("""
        fun main() {
            functionWithUnusedParameter("first", "second")
            functionWithUnusedParameter("used", "unused")
            getHello()
        }
        
        fun functionWithUnusedParameter(used: String, <caret>redundant: String) {
            println("It is used parameter: ${"$"}used")
        }
        
        fun getHello(): String {
            return "Hello!"
        }
        """.trimIndent()
    )

    override val warningQuickFix: String = KotlinBundle.message("remove.parameter.0", "redundant")
    override val warningPossibleArea: String = "redundant"

    override val intentionText: String = KotlinBundle.message("convert.to.expression.body.fix.text")
    override val intentionCaret: String = "return"
    override val intentionPossibleArea: String = intentionCaret
}