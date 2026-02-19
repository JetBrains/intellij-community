// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.refactoring.RefactoringSettings
import com.intellij.refactoring.rename.RenameProcessor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.psi.KtClassOrObject

internal class RenameFileToMatchClassIntention : SelfTargetingRangeIntention<KtClassOrObject>(
    KtClassOrObject::class.java,
    KotlinBundle.messagePointer("rename.file.to.match.top.level.class.name")
) {
    override fun applicabilityRange(element: KtClassOrObject): TextRange? {
        if (!element.isTopLevel()) return null
        val fileName = element.containingKtFile.name
        if (FileUtil.getNameWithoutExtension(fileName) == element.name) return null
        setTextGetter(KotlinBundle.messagePointer("rename.file.to.0.1", element.name.toString(), FileUtilRt.getExtension(fileName)))
        return element.nameIdentifier?.textRange
    }

    override fun startInWriteAction() = false

    override fun applyTo(element: KtClassOrObject, editor: Editor?) {
        val file = element.containingKtFile
        val extension = FileUtilRt.getExtension(file.name)
        RenameProcessor(
            file.project,
            file,
            "${element.name}.$extension",
            RefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FILE,
            RefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_FILE
        ).run()
    }
}