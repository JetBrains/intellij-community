// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.refactoring.suggested

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Parameter
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Signature
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

class KotlinSuggestedRefactoringSupport : SuggestedRefactoringSupport {
    override fun isAnchor(psiElement: PsiElement): Boolean {
        if (psiElement !is KtDeclaration) return false
        if (psiElement is KtParameter && psiElement.ownerFunction != null) return false
        return true
    }
    
    override fun signatureRange(anchor: PsiElement): TextRange? {
        when (anchor) {
            is KtPrimaryConstructor -> return anchor.textRange

            is KtSecondaryConstructor -> return anchor.valueParameterList?.textRange

            is KtCallableDeclaration -> {
                if (isOnlyRenameSupported(anchor)) {
                    return anchor.nameIdentifier?.textRange
                }

                val start = anchor.receiverTypeReference?.textRange?.startOffset
                    ?: anchor.nameIdentifier?.textRange?.startOffset
                    ?: return null
                val end = (anchor.typeReference ?: anchor.valueParameterList ?: anchor.nameIdentifier)
                    ?.textRange?.endOffset
                    ?: return null
                return TextRange(start, end)
            }

            is KtNamedDeclaration -> return anchor.nameIdentifier?.textRange

            else -> return null
        }
    }

    override fun importsRange(psiFile: PsiFile): TextRange? {
        return (psiFile as KtFile).importList?.textRange
    }

    override fun nameRange(anchor: PsiElement): TextRange? {
        val identifier = when (anchor) {
            is KtPrimaryConstructor -> anchor.containingClassOrObject?.nameIdentifier
            is KtSecondaryConstructor -> anchor.getConstructorKeyword()
            is KtNamedDeclaration -> anchor.nameIdentifier
            else -> null
        }
        return identifier?.textRange
    }

    override fun hasSyntaxError(anchor: PsiElement): Boolean {
        if (super.hasSyntaxError(anchor)) return true

        // do not suggest renaming of local variable which has neither type nor initializer
        // it's important because such variable declarations may appear on typing "val name = " before an expression
        if (anchor is KtProperty && anchor.isLocal) {
            if (anchor.typeReference == null && anchor.initializer == null) return true
        }

        return false
    }

    override fun isIdentifierStart(c: Char) = c.isJavaIdentifierStart()
    override fun isIdentifierPart(c: Char) = c.isJavaIdentifierPart()

    override val stateChanges = KotlinSuggestedRefactoringStateChanges(this)
    override val availability = KotlinSuggestedRefactoringAvailability(this)
    override val ui get() = KotlinSuggestedRefactoringUI
    override val execution = KotlinSuggestedRefactoringExecution(this)

    companion object {
        fun isOnlyRenameSupported(declaration: KtCallableDeclaration): Boolean {
            // for local variable - only rename
            return declaration is KtVariableDeclaration && KtPsiUtil.isLocal(declaration)
        }
    }
}

enum class DeclarationType {
    FUN {
        override val prefixKeyword get() = "fun"
        override val isFunction get() = true
    },
    VAL {
        override val prefixKeyword get() = "val"
        override val isFunction get() = false
    },
    VAR {
        override val prefixKeyword get() = "var"
        override val isFunction get() = false
    },
    PRIMARY_CONSTRUCTOR {
        override val prefixKeyword: String?
            get() = null
        override val isFunction get() = true
    },
    SECONDARY_CONSTRUCTOR {
        override val prefixKeyword: String?
            get() = null
        override val isFunction get() = true
    },
    OTHER {
        override val prefixKeyword: String?
            get() = null
        override val isFunction get() = false
    }

    ;

    abstract val prefixKeyword: String?
    abstract val isFunction: Boolean

    companion object {
        fun fromDeclaration(declaration: KtDeclaration): DeclarationType = when (declaration) {
            is KtPrimaryConstructor -> PRIMARY_CONSTRUCTOR

            is KtSecondaryConstructor -> SECONDARY_CONSTRUCTOR

            is KtNamedFunction -> FUN

            is KtProperty -> if (declaration.isVar) VAR else VAL

            else -> OTHER
        }
    }
}

data class KotlinSignatureAdditionalData(
    val declarationType: DeclarationType,
    val receiverType: String?
) : SuggestedRefactoringSupport.SignatureAdditionalData

data class KotlinParameterAdditionalData(
    val defaultValue: String?,
    val modifiers: String
) : SuggestedRefactoringSupport.ParameterAdditionalData

internal val Signature.receiverType: String?
    get() = (additionalData as KotlinSignatureAdditionalData?)?.receiverType

internal val Parameter.defaultValue: String?
    get() = (additionalData as KotlinParameterAdditionalData?)?.defaultValue

val Parameter.modifiers: String
    get() = (additionalData as KotlinParameterAdditionalData?)?.modifiers ?: ""
