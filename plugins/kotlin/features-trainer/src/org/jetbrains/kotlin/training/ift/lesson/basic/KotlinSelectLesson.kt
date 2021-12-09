// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.training.ift.lesson.basic

import training.dsl.LessonSample
import training.dsl.parseLessonSample
import training.learn.lesson.general.NewSelectLesson

class KotlinSelectLesson : NewSelectLesson() {
    override val selectArgument = "\"$selectString\""
    override val selectCall = """someMethod("$firstString", $selectArgument, "$thirdString")"""

    override val numberOfSelectsForWholeCall = 2

    override val sample: LessonSample = parseLessonSample("""
        abstract class Scratch {
            abstract fun someMethod(string1: String, string2: String, string3: String)
            
            fun exampleMethod(condition: Boolean) {
                <select id=1>if (condition) {
                    System.err.println("$beginString")
                    $selectCall
                    System.err.println("$endString")
                }</select>
            }
        }
        """.trimIndent())

    override val selectIf = sample.getPosition(1).selection!!.let { sample.text.substring(it.first, it.second) }
}