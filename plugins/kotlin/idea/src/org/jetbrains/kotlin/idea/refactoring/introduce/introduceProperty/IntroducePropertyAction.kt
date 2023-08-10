// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.introduce.introduceProperty

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.refactoring.RefactoringActionHandler
import org.jetbrains.kotlin.idea.refactoring.KotlinRefactoringSupportProvider
import org.jetbrains.kotlin.idea.refactoring.introduce.AbstractIntroduceAction

class IntroducePropertyAction : AbstractIntroduceAction() {
    override fun getRefactoringHandler(provider: RefactoringSupportProvider): RefactoringActionHandler? =
        (provider as? KotlinRefactoringSupportProvider)?.getIntroducePropertyHandler()
}
