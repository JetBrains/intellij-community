// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.training.ift

import com.intellij.java.ift.JavaLessonsBundle
import com.intellij.util.PlatformUtils
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.training.ift.lesson.basic.KotlinBasicCompletionLesson
import org.jetbrains.kotlin.training.ift.lesson.basic.KotlinContextActionsLesson
import org.jetbrains.kotlin.training.ift.lesson.basic.KotlinSelectLesson
import org.jetbrains.kotlin.training.ift.lesson.basic.KotlinSurroundAndUnwrapLesson
import org.jetbrains.kotlin.training.ift.lesson.completion.KotlinCompletionWithTabLesson
import org.jetbrains.kotlin.training.ift.lesson.completion.KotlinPostfixCompletionLesson
import org.jetbrains.kotlin.training.ift.lesson.completion.KotlinSmartTypeCompletionLesson
import org.jetbrains.kotlin.training.ift.lesson.essential.KotlinOnboardingTourLesson
import org.jetbrains.kotlin.training.ift.lesson.navigation.KotlinDeclarationAndUsagesLesson
import org.jetbrains.kotlin.training.ift.lesson.navigation.KotlinFileStructureLesson
import org.jetbrains.kotlin.training.ift.lesson.navigation.KotlinRecentFilesLesson
import org.jetbrains.kotlin.training.ift.lesson.navigation.KotlinSearchEverywhereLesson
import org.jetbrains.kotlin.training.ift.lesson.refactorings.KotlinRefactoringMenuLesson
import training.dsl.LessonUtil
import training.learn.CourseManager
import training.learn.LessonsBundle
import training.learn.course.LearningCourseBase
import training.learn.course.LearningModule
import training.learn.course.LessonType
import training.learn.lesson.general.*
import training.learn.lesson.general.assistance.CodeFormatLesson
import training.learn.lesson.general.assistance.LocalHistoryLesson
import training.learn.lesson.general.assistance.ParameterInfoLesson
import training.learn.lesson.general.assistance.QuickPopupsLesson
import training.learn.lesson.general.navigation.FindInFilesLesson

class KotlinLearningCourse : LearningCourseBase(KotlinLanguage.INSTANCE.id) {
    override fun modules() = onboardingTour() + stableModules() + CourseManager.instance.findCommonModules("Git")

    private val isOnboardingLessonEnabled: Boolean
        get() = PlatformUtils.isIdeaCommunity() || PlatformUtils.isIdeaUltimate()

    private fun onboardingTour() = if (isOnboardingLessonEnabled) listOf(
        LearningModule(
            id = "Kotlin.Onboarding",
            name = JavaLessonsBundle.message("java.onboarding.module.name"),
            description = JavaLessonsBundle.message("java.onboarding.module.description", LessonUtil.productName),
            primaryLanguage = langSupport,
            moduleType = LessonType.PROJECT
        ) {
            listOf(
                KotlinOnboardingTourLesson()
            )
        }
    )
    else emptyList()

    private fun stableModules() = listOf(
        LearningModule(
            id = "Kotlin.Essential",
            name = LessonsBundle.message("essential.module.name"),
            description = LessonsBundle.message("essential.module.description", LessonUtil.productName),
            primaryLanguage = langSupport,
            moduleType = LessonType.SINGLE_EDITOR  // todo: change to SCRATCH when KTIJ-20742 will be resolved
        ) {
            fun ls(sampleName: String) = loadSample("EditorBasics/$sampleName")
            listOf(
                KotlinContextActionsLesson(),
                GotoActionLesson(ls("Actions.kt.sample"), firstLesson = false),
                KotlinSearchEverywhereLesson(),
                KotlinBasicCompletionLesson(),
            )
        },
        LearningModule(
            id = "Kotlin.EditorBasics",
            name = LessonsBundle.message("editor.basics.module.name"),
            description = LessonsBundle.message("editor.basics.module.description"),
            primaryLanguage = langSupport,
            moduleType = LessonType.SCRATCH
        ) {
            fun ls(sampleName: String) = loadSample("EditorBasics/$sampleName")
            listOf(
                KotlinSelectLesson(),
                CommentUncommentLesson(ls("Comment.kt.sample"), blockCommentsAvailable = true),
                DuplicateLesson(ls("Duplicate.kt.sample")),
                MoveLesson("run()", ls("Move.kt.sample")),
                CollapseLesson(ls("Collapse.kt.sample")),
                KotlinSurroundAndUnwrapLesson(),
                MultipleSelectionHtmlLesson(),
            )
        },
        LearningModule(
            id = "Kotlin.CodeCompletion",
            name = LessonsBundle.message("code.completion.module.name"),
            description = LessonsBundle.message("code.completion.module.description"),
            primaryLanguage = langSupport,
            moduleType = LessonType.SINGLE_EDITOR  // todo: change to SCRATCH when KTIJ-20742 will be resolved
        ) {
            listOf(
                KotlinBasicCompletionLesson(),
                KotlinSmartTypeCompletionLesson(),
                KotlinPostfixCompletionLesson(),
                // TODO: KotlinStatementCompletionLesson
                KotlinCompletionWithTabLesson()
            )
        },
        LearningModule(
            id = "Kotlin.Refactorings",
            name = LessonsBundle.message("refactorings.module.name"),
            description = LessonsBundle.message("refactorings.module.description"),
            primaryLanguage = langSupport,
            moduleType = LessonType.SINGLE_EDITOR
        ) {
            fun ls(sampleName: String) = loadSample("Refactorings/$sampleName")
            listOf(
                // TODO: KotlinRenameLesson(),
                // TODO: ExtractVariableFromBubbleLesson(ls("ExtractVariable.java.sample")),
                // TODO: KotlinExtractMethodCocktailSortLesson(),
                KotlinRefactoringMenuLesson(),
            )
        },
        LearningModule(
            id = "Kotlin.CodeAssistance",
            name = LessonsBundle.message("code.assistance.module.name"),
            description = LessonsBundle.message("code.assistance.module.description"),
            primaryLanguage = langSupport,
            moduleType = LessonType.SINGLE_EDITOR
        ) {
            fun ls(sampleName: String) = loadSample("CodeAssistance/$sampleName")
            listOf(
                LocalHistoryLesson(),
                CodeFormatLesson(ls("CodeFormat.kt.sample"), true),
                ParameterInfoLesson(ls("ParameterInfo.kt.sample")),
                QuickPopupsLesson(ls("QuickPopups.kt.sample")),
                // TODO: KotlinEditorCodingAssistanceLesson(ls("EditorCodingAssistance.java.sample")),
            )
        },
        LearningModule(
            id = "Kotlin.Navigation",
            name = LessonsBundle.message("navigation.module.name"),
            description = LessonsBundle.message("navigation.module.description"),
            primaryLanguage = langSupport,
            moduleType = LessonType.PROJECT
        ) {
            listOf(
                KotlinSearchEverywhereLesson(),
                FindInFilesLesson("src/warehouse/FindInFilesSample.kt"),
                KotlinFileStructureLesson(),
                KotlinDeclarationAndUsagesLesson(),
                // TODO: KotlinInheritanceHierarchyLesson
                KotlinRecentFilesLesson()
                // TODO: KotlinOccurrencesLesson
            )
        },
        /*LearningModule(id = "Kotlin.RunAndDebug",
                       name = LessonsBundle.message("run.debug.module.name"),
                       description = LessonsBundle.message("run.debug.module.description"),
                       primaryLanguage = langSupport,
                       moduleType = LessonType.SINGLE_EDITOR) {
            listOf(
                // TODO: KotlinRunConfigurationLesson(),
                // TODO: KotlinDebugLesson(),
            )
        },*/
    )

    override fun getLessonIdToTipsMap(): Map<String, List<String>> = mutableMapOf(
        // Essential
        "context.actions" to listOf("ContextActions"),
        "Actions" to listOf("find_action", "GoToAction"),
        "Search everywhere" to listOf("SearchEverywhere", "GoToClass", "search_everywhere_general"),
        "Basic completion" to listOf("CodeCompletion"),

        // EditorBasics
        "Select" to listOf("smart_selection", "CtrlW"),
        "Comment line" to listOf("CommentCode"),
        "Duplicate" to listOf("CtrlD", "DeleteLine"),
        "Move" to listOf("MoveUpDown"),
        "Surround and unwrap" to listOf("SurroundWith"),

        // CodeCompletion
        "Basic completion" to listOf("CodeCompletion"),

        // Navigation
        "Search everywhere" to listOf("SearchEverywhere", "GoToClass", "search_everywhere_general"),
        "Find in files" to listOf("FindReplaceToggle", "FindInPath"),
        "File structure" to listOf("FileStructurePopup"),
    ).also { map ->
        val gitCourse = CourseManager.instance.findCommonCourseById("Git")
        if (gitCourse != null) {
            map.putAll(gitCourse.getLessonIdToTipsMap())
        }
    }
}