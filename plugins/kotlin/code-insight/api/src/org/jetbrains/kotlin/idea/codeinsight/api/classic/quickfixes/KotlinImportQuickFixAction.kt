// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes

import com.intellij.codeInsight.hint.QuestionAction
import com.intellij.openapi.editor.Editor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

@ApiStatus.Internal
abstract class KotlinImportQuickFixAction<out T : KtElement>(element: T): KotlinQuickFixAction<T>(element) {
    /**
     * @return import action if quick fix element is still valid, null, otherwise.
     * Note that provided action might require addition actions by the user (e.g. when there are multiple available imports to choose from).
     */
    abstract fun createImportAction(
        editor: Editor,
        file: KtFile,
    ): QuestionAction?

    /**
     * @return import action if quick fix can be applied without any additional actions by the user, null, otherwise.
     */
    abstract fun createAutoImportAction(
        editor: Editor,
        file: KtFile,
        filterSuggestions: (Collection<FqName>) -> Collection<FqName> = { suggestions -> suggestions },
    ): QuestionAction?
}