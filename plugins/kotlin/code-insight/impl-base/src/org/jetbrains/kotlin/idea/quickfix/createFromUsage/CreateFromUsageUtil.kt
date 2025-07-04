// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.createFromUsage

import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.isAncestor
import org.jetbrains.kotlin.idea.base.psi.isMultiLine
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.core.insertMembersAfterAndReformat
import org.jetbrains.kotlin.idea.core.isKotlinNotebookCell
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import kotlin.math.max

object CreateFromUsageUtil {
    // TODO: Simplify and use formatter as much as possible
    @Suppress("UNCHECKED_CAST")
    fun <D : KtNamedDeclaration> placeDeclarationInContainer(
      declaration: D,
      container: PsiElement,
      anchor: PsiElement?,
      fileToEdit: PsiFile = container.containingFile
    ): D {
        val psiFactory = KtPsiFactory(container.project)
        val newLine = psiFactory.createNewLine()

        val actualContainer = (container as? KtClassOrObject)?.getOrCreateBody() ?: getActualContainerForScript(container)

        val declarationInPlace = when {
            declaration is KtPrimaryConstructor -> {
                val primaryConstructor = (container as? KtClass)?.createPrimaryConstructorIfAbsent()
                primaryConstructor?.replaced(declaration) ?: declaration
            }

            declaration is KtProperty && container !is KtBlockExpression -> {
                val sibling = actualContainer.getChildOfType<KtProperty>() ?: when (actualContainer) {
                    is KtClassBody -> actualContainer.declarations.firstOrNull() ?: actualContainer.rBrace
                    is KtFile -> actualContainer.declarations.first()
                    is KtBlockExpression -> actualContainer.statements.firstOrNull() ?: actualContainer.rBrace
                    else -> null
                }
                sibling?.let { actualContainer.addBefore(declaration, it) as D } ?: fileToEdit.add(declaration) as D
            }
            declaration is KtParameter -> {
                val sibling = when (actualContainer) {
                    is KtParameterList -> actualContainer.rightParenthesis
                    else -> error("Invalid container: $actualContainer for parameter $declaration\n${actualContainer.text}")
                }
                sibling?.let {
                    actualContainer.addBefore(declaration, it) as D
                } ?: fileToEdit.add(declaration) as D
            }

            anchor != null && actualContainer.isAncestor(anchor, true) -> {
                val insertToBlock = container is KtBlockExpression
                if (insertToBlock) {
                    val parent = container.parent
                    if (parent is KtFunctionLiteral) {
                        if (!parent.isMultiLine()) {
                            parent.addBefore(newLine, container)
                            parent.addAfter(newLine, container)
                        }
                    }
                }
                val addBefore = insertToBlock ||
                        declaration is KtTypeAlias ||
                        // In K2 REPL model, notebook cells are somewhat similar to function bodies,
                        // so we should add declarations before their usage
                        container.isKotlinNotebookCell
                addNextToOriginalElementContainer(addBefore, anchor, declaration, actualContainer)
            }

            container is KtFile -> container.add(declaration) as D

            container is PsiClass -> {
                if (declaration is KtSecondaryConstructor) {
                    val wrappingClass = psiFactory.createClass("class ${container.name} {\n}")
                    addDeclarationToClassOrObject(wrappingClass, declaration)
                    (fileToEdit.add(wrappingClass) as KtClass).declarations.first() as D
                } else {
                    fileToEdit.add(declaration) as D
                }
            }

            container is KtClassOrObject -> {
                val sibling: PsiElement? = container.declarations.lastOrNull { it::class == declaration::class }
                insertMembersAfterAndReformat(null, container, declaration, sibling)
            }
            else -> throw KotlinExceptionWithAttachments("Unknown containing element: ${container::class.java}")
                .withPsiAttachment("container", container)
        }

        when (declaration) {
            is KtEnumEntry -> {
                val prevEnumEntry = declarationInPlace.siblings(forward = false, withItself = false).firstIsInstanceOrNull<KtEnumEntry>()
                if (prevEnumEntry != null) {
                    if ((prevEnumEntry.prevSibling as? PsiWhiteSpace)?.text?.contains('\n') == true) {
                        declarationInPlace.parent.addBefore(psiFactory.createNewLine(), declarationInPlace)
                    }
                    val comma = psiFactory.createComma()
                    if (prevEnumEntry.allChildren.any { it.node.elementType == KtTokens.COMMA }) {
                        declarationInPlace.add(comma)
                    } else {
                        prevEnumEntry.add(comma)
                    }
                    val semicolon = prevEnumEntry.allChildren.firstOrNull { it.node?.elementType == KtTokens.SEMICOLON }
                    if (semicolon != null) {
                      (semicolon.prevSibling as? PsiWhiteSpace)?.text?.let {
                            declarationInPlace.add(psiFactory.createWhiteSpace(it))
                        }
                        declarationInPlace.add(psiFactory.createSemicolon())
                        semicolon.delete()
                    }
                }
            }
            !is KtPrimaryConstructor -> {
                val parent = declarationInPlace.parent
                if (parent !is KtParameterList) {
                    calcNecessaryEmptyLines(declarationInPlace, false).let {
                        if (it > 0) parent.addBefore(psiFactory.createNewLine(it), declarationInPlace)
                    }
                    calcNecessaryEmptyLines(declarationInPlace, true).let {
                        if (it > 0) parent.addAfter(psiFactory.createNewLine(it), declarationInPlace)
                    }
                }
            }
        }
        return declarationInPlace
    }

    fun patchVisibilityForInlineFunction(expression: KtCallExpression): KtModifierKeywordToken? {
        val parentFunction = expression.getStrictParentOfType<KtNamedFunction>()
        return if (parentFunction?.hasModifier(KtTokens.INLINE_KEYWORD) == true) {
            when {
                parentFunction.isPublic -> (KtTokens.PUBLIC_KEYWORD)
                parentFunction.isProtected() -> (KtTokens.PROTECTED_KEYWORD)
                else -> null
            }
        } else {
            null
        }
    }

    fun computeDefaultVisibilityAsJvmModifier(
      containingElement: PsiElement,
      isAbstract: Boolean,
      isExtension: Boolean,
      isConstructor: Boolean,
      originalElement: PsiElement
    ): JvmModifier? {
        return if (isAbstract) null
        else if ((containingElement is KtClassOrObject || containingElement is PsiClass)
            && (isExtension || !(containingElement is KtClass && containingElement.isInterface() || containingElement is PsiClass && containingElement.isInterface))
            && (containingElement.isAncestor(originalElement) || isExtension && containingElement.containingFile == originalElement.containingFile)
            && !isConstructor
        ) JvmModifier.PRIVATE
        else if (isExtension) {
            if (containingElement is KtFile && containingElement.isScript()) null else JvmModifier.PRIVATE
        } else null
    }

    private val visibilityModifierToKotlinToken: Map<JvmModifier, KtModifierKeywordToken> = mapOf(
      JvmModifier.PRIVATE to KtTokens.PRIVATE_KEYWORD,
      JvmModifier.PACKAGE_LOCAL to KtTokens.INTERNAL_KEYWORD,
      JvmModifier.PROTECTED to KtTokens.PROTECTED_KEYWORD,
      JvmModifier.PUBLIC to KtTokens.PUBLIC_KEYWORD,
    )

    fun visibilityModifierToString(modifier: JvmModifier?): String? =
        visibilityModifierToKotlinToken[modifier]?.takeIf { it != KtTokens.PUBLIC_KEYWORD }?.value

    private val modifierToKotlinToken: Map<JvmModifier, KtModifierKeywordToken> =
        visibilityModifierToKotlinToken + mapOf(JvmModifier.ABSTRACT to KtTokens.ABSTRACT_KEYWORD)

    fun jvmModifierToKotlin(modifier: JvmModifier?): KtModifierKeywordToken? =
        modifierToKotlinToken[modifier]

    private fun calcNecessaryEmptyLines(decl: KtDeclaration, after: Boolean): Int {
        var lineBreaksPresent = 0
        var neighbor: PsiElement? = null

        siblingsLoop@
        for (sibling in decl.siblings(forward = after, withItself = false)) {
            when (sibling) {
                is PsiWhiteSpace -> lineBreaksPresent += (sibling.text ?: "").count { it == '\n' }
                else -> {
                    neighbor = sibling
                    break@siblingsLoop
                }
            }
        }

        val neighborType = neighbor?.node?.elementType
        val lineBreaksNeeded = when {
          neighborType == KtTokens.LBRACE || neighborType == KtTokens.RBRACE -> 1
          neighbor is KtDeclaration && (neighbor !is KtProperty || decl !is KtProperty) -> 2
            else -> 1
        }

        return max(lineBreaksNeeded - lineBreaksPresent, 0)
    }
    fun addDeclarationToClassOrObject(
      classOrObject: KtClassOrObject,
      declaration: KtNamedDeclaration
    ): KtNamedDeclaration {
        val classBody = classOrObject.getOrCreateBody()
        return if (declaration is KtNamedFunction) {
            val neighbor = PsiTreeUtil.skipSiblingsBackward(
              classBody.rBrace ?: classBody.lastChild!!,
              PsiWhiteSpace::class.java
            )
            classBody.addAfter(declaration, neighbor) as KtNamedDeclaration
        } else classBody.addAfter(declaration, classBody.lBrace!!) as KtNamedDeclaration
    }


    private fun <D : KtNamedDeclaration> addNextToOriginalElementContainer(
        addBefore: Boolean,
        anchor: PsiElement,
        declaration: D,
        actualContainer: PsiElement
    ): D {
        val sibling = anchor.parentsWithSelf.first { it.parent == actualContainer }
        @Suppress("UNCHECKED_CAST")
        return if (addBefore || PsiTreeUtil.hasErrorElements(sibling)) {
            actualContainer.addBefore(declaration, sibling)
        } else {
            actualContainer.addAfter(declaration, sibling)
        } as D
    }

    /**
     * In scripts, no elements should exist outside the main block expression
     */
    private fun getActualContainerForScript(container: PsiElement): PsiElement {
        return if ((container as? KtFile)?.isScript() == true) {
            container.script?.blockExpression ?: container
        } else {
            container
        }
    }
}