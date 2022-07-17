// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.training.ift.lesson.basic

import com.intellij.codeInsight.completion.BaseCompletionLookupArranger
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.java.ift.lesson.completion.JavaBasicCompletionLesson
import org.jetbrains.kotlin.training.ift.KotlinLessonsBundle
import training.dsl.LessonContext
import training.dsl.parseLessonSample
import training.dsl.usePreviouslyFoundListItem
import training.learn.LessonsBundle

class KotlinBasicCompletionLesson : JavaBasicCompletionLesson() {
    private val sample = parseLessonSample(
        """
        
        private const val INITIAL = 173

        fun createList(): List<Int> = <caret>
        """.trimIndent()
    )

    override val lessonContent: LessonContext.() -> Unit = {
        prepareSample(sample)

        task {
            text(LessonsBundle.message("basic.completion.start.typing", code("list")))
            triggerAndBorderHighlight().listItem { item -> isListOfCompletionItem(item) }
            restoreIfTypedIncorrectly(sample, "listOf")
            test { type("li") }
        }

        task {
            text(KotlinLessonsBundle.message("kotlin.basic.completion.choose.item", code("listOf(element: T)")))
            stateCheck {
                val text = editor.document.charsSequence
                val caretOffset = editor.caretModel.currentCaret.offset
                if (caretOffset in 1 until text.length) {
                    text.contains("listOf()") && text[caretOffset - 1] == '(' && text[caretOffset] == ')'
                } else false
            }
            restoreByUi()
            restoreIfTypedIncorrectly(sample, "listOf")
            test(waitEditorToBeReady = false) {
                usePreviouslyFoundListItem { it.doubleClick() }
            }
        }

        invokeCompletionTasks("INITIAL", "listOf(INITIAL)")

        epilogue()
    }

    private fun isListOfCompletionItem(item: Any): Boolean {
        val lookupElement = item as? LookupElementDecorator<*> ?: return false
        val presentation = BaseCompletionLookupArranger.getDefaultPresentation(lookupElement) ?: return false
        return presentation.itemText == "listOf" && presentation.tailFragments.firstOrNull()?.text == "(element: T)"
    }
}