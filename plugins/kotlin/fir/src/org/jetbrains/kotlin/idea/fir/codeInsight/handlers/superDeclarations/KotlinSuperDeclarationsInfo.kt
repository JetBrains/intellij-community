package org.jetbrains.kotlin.idea.fir.codeInsight.handlers.superDeclarations

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.fir.codeInsight.handlers.HLGotoSuperActionHandler

data class KotlinSuperDeclarationsInfo(val superDeclarations: List<PsiElement>, val kind: DeclarationKind) {
    enum class DeclarationKind {
        CLASS, PROPERTY, FUNCTION
    }
}

