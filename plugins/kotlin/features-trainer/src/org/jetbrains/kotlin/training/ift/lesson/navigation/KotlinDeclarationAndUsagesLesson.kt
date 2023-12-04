// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.training.ift.lesson.navigation

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtCallExpression
import training.dsl.LessonContext
import training.learn.lesson.general.navigation.DeclarationAndUsagesLesson

class KotlinDeclarationAndUsagesLesson : DeclarationAndUsagesLesson() {
    override fun LessonContext.setInitialPosition(): Unit = caret("foo()")
    override val sampleFilePath: String get() = "src/DerivedClass2.kt"
    override val entityName: String = "foo"

    override fun getParentExpression(element: PsiElement): PsiElement? {
        return element.takeIf { element.parentOfType<KtCallExpression>() != null }
    }
}
