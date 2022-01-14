// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.training.ift.lesson.basic

import com.intellij.codeInsight.completion.BaseCompletionLookupArranger
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.java.ift.lesson.completion.JavaBasicCompletionLesson
import org.assertj.swing.fixture.JListFixture
import org.jetbrains.kotlin.training.ift.KotlinLessonsBundle
import training.dsl.LessonContext
import training.dsl.parseLessonSample
import training.learn.LessonsBundle
import javax.swing.JList

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
            triggerByListItemAndHighlight l@{ item -> isListOfCompletionItem(item) }
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
                val list = previous.ui as? JList<*> ?: error("The list with completion items not found")
                val model = list.model
                val index = (0 until model.size).map { model.getElementAt(it) }.indexOfFirst { isListOfCompletionItem(it) }
                if (index == -1) error("The listOf(element: T) completion item not found")
                JListFixture(robot, list).item(index).doubleClick()
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