// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.training.ift.lesson.refactorings

import com.intellij.java.ift.JavaLessonsBundle
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.training.ift.KotlinLessonsBundle
import training.dsl.LessonContext
import training.dsl.LessonSample
import training.dsl.LessonUtil
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.dsl.dropMnemonic
import training.dsl.parseLessonSample
import training.dsl.restoreRefactoringOptionsInformer
import training.learn.lesson.general.refactorings.RefactoringMenuLessonBase
import training.util.adaptToNotNativeLocalization

class KotlinRefactoringMenuLesson : RefactoringMenuLessonBase("java.refactoring.menu") {
    override val sample: LessonSample = parseLessonSample(
        """
import java.io.FileReader
import java.io.BufferedReader

fun main() {
    val list = readStrings()
    val filtered = list.filter { it.isNotEmpty() }
    filtered.forEach { 
        println(it)
    }
}

fun readStrings(): List<String> {
    return BufferedReader(<select>FileReader("input.txt")</select>).readLines()
}""".trimIndent())

    override val lessonContent: LessonContext.() -> Unit = {
        extractParameterTasks()
        moreRefactoringsTasks()
        restoreRefactoringOptionsInformer()
    }

    private fun LessonContext.moreRefactoringsTasks() {
        waitBeforeContinue(300)

        val inlineVariableName = "list"

        caret(inlineVariableName)

        actionTask("Inline") {
            restoreIfModifiedOrMoved()
            if (adaptToNotNativeLocalization) {
                JavaLessonsBundle.message(
                    "java.refactoring.menu.inline.variable", code(inlineVariableName),
                    action("Refactorings.QuickListPopupAction"), strong(KotlinBundle.message("title.inline.property")),
                    action(it)
                )
            } else KotlinLessonsBundle.message(
                "kotlin.refactoring.menu.inline.property.eng",
                code(inlineVariableName), action("Refactorings.QuickListPopupAction"), action(it)
            )
        }
        task {
            stateCheck {
                !editor.document.charsSequence.contains(inlineVariableName)
            }
        }

        caret("txt", true)

        actionTask("IntroduceConstant") {
            restoreIfModifiedOrMoved()
            if (adaptToNotNativeLocalization) {
                JavaLessonsBundle.message(
                    "java.refactoring.menu.introduce.constant", action("Refactorings.QuickListPopupAction"),
                    strong(KotlinBundle.message("introduce.constant").dropMnemonic()), action(it)
                )
            } else JavaLessonsBundle.message(
                "java.refactoring.menu.introduce.constant.eng",
                action("Refactorings.QuickListPopupAction"), action(it)
            )
        }

        actionTask("NextTemplateVariable") {
            KotlinLessonsBundle.message("kotlin.refactoring.menu.confirm.constant", LessonUtil.rawEnter())
        }
    }
}
