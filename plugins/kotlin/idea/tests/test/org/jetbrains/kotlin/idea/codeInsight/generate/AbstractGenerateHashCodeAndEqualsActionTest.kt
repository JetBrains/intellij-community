// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.generate

import com.intellij.codeInsight.actions.CodeInsightAction
import org.jetbrains.kotlin.idea.actions.generate.KotlinGenerateEqualsAndHashcodeAction

abstract class AbstractK1GenerateHashCodeAndEqualsActionTest: AbstractGenerateHashCodeAndEqualsActionTest() {
    override fun createAction(fileText: String): CodeInsightAction = KotlinGenerateEqualsAndHashcodeAction()
}
