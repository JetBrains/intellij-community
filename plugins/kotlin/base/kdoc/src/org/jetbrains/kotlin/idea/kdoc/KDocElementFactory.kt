// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.kdoc

import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType

class KDocElementFactory(val project: Project) {
    fun createKDocFromText(text: String): KDoc {
        val fileText = "$text fun foo { }"
        val function = KtPsiFactory(project).createDeclaration<KtFunction>(fileText)
        return PsiTreeUtil.findChildOfType(function, KDoc::class.java)!!
    }

    fun createNameFromText(text: String): KDocName {
        val kdocText = "/** @param $text foo*/"
        val kdoc = createKDocFromText(kdocText)
        val section = kdoc.getDefaultSection()
        val tag = section.findTagByName("param")
        val link = tag?.getSubjectLink() ?: throw IllegalArgumentException("Cannot find subject link in doc comment '$kdocText'")
        return link.getChildOfType()!!
    }
}