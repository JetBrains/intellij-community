// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.training.ift.lesson.essential


import com.intellij.java.ift.lesson.essential.OnboardingTourLessonBase
import com.intellij.openapi.ui.popup.Balloon
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.training.ift.KotlinLessonsBundle
import training.dsl.*
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.util.*

class KotlinOnboardingTourLesson : OnboardingTourLessonBase("kotlin.onboarding") {
    override val demoFileExtension: String = "kt"
    override val learningProjectName: String = "KotlinLearningProject"
    private val samplePrintln = "println(\"AVERAGE of array \" + array.joinToString() + \" is \" + findAverage(array))"
    override val sample: LessonSample = parseLessonSample("""
    fun findAverage(values: IntArray): Double {
        var result = 0.0
        for (i in 0 un<caret id=3/>til values.size) {
            result += values[i]
        }
        <caret>return result<caret id=2/>
    }
    
    fun main() {
        val array = intArrayOf(5, 6, 7, 8)
        $samplePrintln
    }
    """.trimIndent())

    override val completionStepExpectedCompletion: String = "size"

    override fun LessonContext.contextActions() {
        val quickFixMessage = KotlinBundle.message("replace.index.loop.with.collection.loop.quick.fix.text")
        caret(sample.getPosition(3))

        task {
            triggerOnEditorText("until", highlightBorder = true)
        }

        task("ShowIntentionActions") {
            text(KotlinLessonsBundle.message("kotlin.onboarding.invoke.intention.for.warning.1"))
            text(KotlinLessonsBundle.message("kotlin.onboarding.invoke.intention.for.warning.2", action(it)))
            text(KotlinLessonsBundle.message("kotlin.onboarding.invoke.intention.for.warning.balloon", action(it)),
                 LearningBalloonConfig(Balloon.Position.above, width = 0, cornerToPointerDistance = 80))
            triggerAndBorderHighlight().listItem { item ->
                item.isToStringContains(quickFixMessage)
            }
            restoreIfModifiedOrMoved()
        }

        task {
            text(KotlinLessonsBundle.message("kotlin.onboarding.select.fix", strong(quickFixMessage)))
            stateCheck {
                editor.document.text.contains("for (element in values)")
            }
            restoreByUi(delayMillis = defaultRestoreDelay)
        }

        fun getIntentionMessage(): @Nls String {
            return KotlinBundle.message("convert.concatenation.to.template")
        }

        caret("RAGE")

        task {
            triggerOnEditorText("AVERAGE")
        }

        task("ShowIntentionActions") {
            text(KotlinLessonsBundle.message("kotlin.onboarding.invoke.intention.for.code", action(it)))
            text(KotlinLessonsBundle.message("kotlin.onboarding.invoke.intention.for.code.balloon", action(it)),
                 LearningBalloonConfig(Balloon.Position.below, width = 0))
            val intentionMessage = getIntentionMessage()
            triggerAndBorderHighlight().listItem { item ->
                item.isToStringContains(intentionMessage)
            }
            restoreIfModifiedOrMoved()
        }

        task {
            text(KotlinLessonsBundle.message("kotlin.onboarding.apply.intention", strong(getIntentionMessage()), LessonUtil.rawEnter()))
            stateCheck {
                val text = editor.document.text
                text.contains("\${array.joinToString()}")
            }
            restoreByUi(delayMillis = defaultRestoreDelay)
        }
    }
}