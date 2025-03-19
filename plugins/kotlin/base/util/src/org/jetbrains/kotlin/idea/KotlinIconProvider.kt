// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea

import com.intellij.icons.AllIcons
import com.intellij.ide.IconProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.IconManager
import com.intellij.ui.RowIcon
import com.intellij.util.PlatformIcons
import org.jetbrains.kotlin.analysis.decompiled.light.classes.KtLightClassForDecompiledDeclarationBase
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.elements.KtLightParameter
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.idea.KotlinIcons.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import javax.swing.Icon

abstract class KotlinIconProvider : IconProvider(), DumbAware {
    protected abstract fun isMatchingExpected(declaration: KtDeclaration): Boolean

    private fun Icon.addExpectActualMarker(element: PsiElement): Icon {
        val declaration = (element as? KtNamedDeclaration) ?: return this
        val additionalIcon = when {
            isExpectDeclaration(declaration) -> EXPECT
            isMatchingExpected(declaration) -> ACTUAL
            else -> return this
        }
        return RowIcon(2).apply {
            setIcon(this@addExpectActualMarker, 0)
            setIcon(additionalIcon, 1)
        }
    }

    private tailrec fun isExpectDeclaration(declaration: KtDeclaration): Boolean {
        if (declaration.hasExpectModifier()) {
            return true
        }

        val containingDeclaration = declaration.containingClassOrObject ?: return false
        return isExpectDeclaration(containingDeclaration)
    }

    override fun getIcon(psiElement: PsiElement, flags: Int): Icon? {
        if (psiElement is KtFile) {
            if (psiElement.isScript()) {
                return psiElement.scriptIcon()
            }
            val mainClass = getSingleClass(psiElement)
            return if (mainClass != null) getIcon(mainClass, flags) else FILE
        }

        val result = psiElement.getBaseIcon()
        if (flags and Iconable.ICON_FLAG_VISIBILITY > 0 && result != null && (psiElement is KtModifierListOwner && psiElement !is KtClassInitializer)) {
            val list = psiElement.modifierList
            val visibilityIcon = getVisibilityIcon(list)

            val withExpectedActual: Icon = try {
                result.addExpectActualMarker(psiElement)
            } catch (indexNotReady: IndexNotReadyException) {
                result
            }

            return createRowIcon(withExpectedActual, visibilityIcon)
        }
        return result
    }

    companion object {
        fun isSingleClassFile(file: KtFile) = getSingleClass(file) != null

        fun getSingleClass(file: KtFile): KtClassOrObject? {
            var targetDeclaration: KtDeclaration? = null
            for (declaration: KtDeclaration in file.declarations) {
                if (!declaration.isPrivate() && declaration !is KtTypeAlias) {
                    if (targetDeclaration != null) return null
                    targetDeclaration = declaration
                }
            }
            return targetDeclaration?.takeIf { it is KtClassOrObject && StringUtil.getPackageName(file.name) == it.name } as? KtClassOrObject
        }

        fun getMainClass(file: KtFile): KtClassOrObject? {
            var targetClassOrObject: KtClassOrObject? = null
            for (declaration in file.declarations) {
                if (!declaration.isPrivate() && declaration is KtClassOrObject) {
                    if (targetClassOrObject != null) return null
                    targetClassOrObject = declaration
                }
            }
            return targetClassOrObject?.takeIf { StringUtil.getPackageName(file.name) == it.name }
        }

        private fun createRowIcon(baseIcon: Icon, visibilityIcon: Icon): RowIcon {
            val rowIcon = RowIcon(2)
            rowIcon.setIcon(baseIcon, 0)
            rowIcon.setIcon(visibilityIcon, 1)
            return rowIcon
        }

        fun getVisibilityIcon(list: KtModifierList?): Icon {
            val icon: com.intellij.ui.PlatformIcons? = if (list != null) {
                when {
                    list.hasModifier(KtTokens.PRIVATE_KEYWORD) -> com.intellij.ui.PlatformIcons.Private
                    list.hasModifier(KtTokens.PROTECTED_KEYWORD) -> com.intellij.ui.PlatformIcons.Protected
                    list.hasModifier(KtTokens.INTERNAL_KEYWORD) -> com.intellij.ui.PlatformIcons.Local
                    else -> null
                }
            } else {
                null
            }

            return (icon ?: com.intellij.ui.PlatformIcons.Public).let(IconManager.getInstance()::getPlatformIcon)
        }

        private fun PsiFile.scriptIcon(): Icon = when {
            virtualFile.name.endsWith(".gradle.kts") -> GRADLE_SCRIPT
            else -> SCRIPT
        }

        fun PsiElement.getBaseIcon(): Icon? = when (this) {
            is KtPackageDirective -> AllIcons.Nodes.Package
            is KtFile, is KtLightClassForFacade -> FILE
            is KtScript -> (parent as? KtFile)?.scriptIcon()
            is KtLightClass -> navigationElement.getBaseIcon()
            is KtNamedFunction -> when {
                receiverTypeReference != null ->
                    if (KtPsiUtil.isAbstract(this)) ABSTRACT_EXTENSION_FUNCTION else EXTENSION_FUNCTION
                getStrictParentOfType<KtNamedDeclaration>() is KtClass ->
                    if (KtPsiUtil.isAbstract(this)) PlatformIcons.ABSTRACT_METHOD_ICON else
                        IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Method)
                else ->
                    FUNCTION
            }
            is KtConstructor<*> -> IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Method)
            is KtLightMethod -> when(val u = unwrapped) {
                is KtProperty -> if (!u.hasBody()) PlatformIcons.ABSTRACT_METHOD_ICON else
                    IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Method)
                else -> IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Method)
            }
            is KtLightParameter -> IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Variable)
            is KtFunctionLiteral -> LAMBDA
            is KtClass -> when {
                isInterface() -> INTERFACE
                isEnum() -> ENUM
                isAnnotation() -> ANNOTATION
                this is KtEnumEntry && getPrimaryConstructorParameterList() == null -> ENUM
                else -> if (isAbstract()) ABSTRACT_CLASS else CLASS
            }
            is KtObjectDeclaration -> OBJECT
            is KtParameter -> {
                if (KtPsiUtil.getClassIfParameterIsProperty(this) != null) {
                    if (isMutable) FIELD_VAR else FIELD_VAL
                } else
                    PARAMETER
            }
            is KtProperty -> if (isVar) FIELD_VAR else FIELD_VAL
            is KtScriptInitializer -> LAMBDA
            is KtClassInitializer -> CLASS_INITIALIZER
            is KtTypeAlias -> TYPE_ALIAS
            is KtAnnotationEntry -> {
                (shortName?.asString() == JvmFileClassUtil.JVM_NAME_SHORT).ifTrue {
                    val grandParent = parent.parent
                    if (grandParent is KtPropertyAccessor) {
                        grandParent.property.getBaseIcon()
                    } else {
                        grandParent.getBaseIcon()
                    }
                }
            }
            is PsiClass -> (this is KtLightClassForDecompiledDeclarationBase).ifTrue {
                val origin = (this as? KtLightClass)?.kotlinOrigin
                //TODO (light classes for decompiled files): correct presentation
                if (origin != null) origin.getBaseIcon() else CLASS
            } ?: getBaseIconUnwrapped()
            else -> getBaseIconUnwrapped()
        }

        private fun PsiElement.getBaseIconUnwrapped(): Icon? = unwrapped?.takeIf { it != this }?.getBaseIcon()
    }
}
