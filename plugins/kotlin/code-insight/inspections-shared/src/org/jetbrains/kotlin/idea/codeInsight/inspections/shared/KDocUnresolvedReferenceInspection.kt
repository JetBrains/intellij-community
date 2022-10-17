// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName

class KDocUnresolvedReferenceInspection() : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        KDocUnresolvedReferenceVisitor(holder)

    private class KDocUnresolvedReferenceVisitor(private val holder: ProblemsHolder) : PsiElementVisitor() {
        override fun visitElement(element: PsiElement) {
            if (element is KDocName) {
                val ref = element.mainReference
                if (ref.resolve() == null) {
                    holder.registerProblem(ref)
                }
            }
        }
    }
}