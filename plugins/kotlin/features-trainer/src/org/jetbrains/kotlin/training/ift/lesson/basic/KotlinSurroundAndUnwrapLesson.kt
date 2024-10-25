// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.training.ift.lesson.basic

import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import training.dsl.LessonSample
import training.dsl.parseLessonSample
import training.learn.lesson.general.SurroundAndUnwrapLesson

class KotlinSurroundAndUnwrapLesson : SurroundAndUnwrapLesson() {
    override val sample: LessonSample = parseLessonSample("""
        
        fun main(args: Array<String>) {
            <select>println("Surround and Unwrap me!")</select>
        }
        """.trimIndent())

    override val surroundItems = arrayOf("try", "catch", "finally")

    override val lineShiftBeforeUnwrap = -2

    override val unwrapTryText = KotlinBundle.message("unwrap.expression", "try {${Typography.ellipsis}}")
}