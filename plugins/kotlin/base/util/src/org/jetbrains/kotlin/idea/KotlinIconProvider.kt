// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea

import com.intellij.icons.AllIcons
import com.intellij.ide.IconProvider
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.IconManager
import com.intellij.ui.RowIcon
import com.intellij.util.PlatformIcons
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaLocalVariableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPackageSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolLocation
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.decompiled.light.classes.KtLightClassForDecompiledDeclarationBase
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.elements.KtLightParameter
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.idea.KotlinIcons.*
import org.jetbrains.kotlin.idea.base.util.KotlinSingleClassFileAnalyzer
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
            val mainClass = KotlinSingleClassFileAnalyzer.getSingleClass(psiElement)
            return if (mainClass != null) getIcon(mainClass, flags) else FILE
        }

        val result = psiElement.getBaseIcon()
        if (flags and Iconable.ICON_FLAG_VISIBILITY > 0 && result != null && (psiElement is KtModifierListOwner && psiElement !is KtClassInitializer)) {
            val list = psiElement.modifierList
            val visibilityIcon = getVisibilityIcon(list)

            val withExpectedActual: Icon = try {
                result.addExpectActualMarker(psiElement)
            } catch (_: IndexNotReadyException) {
                result
            }

            return createRowIcon(withExpectedActual, visibilityIcon)
        }
        return result
    }

    open fun getVisibilityIcon(list: KtModifierList?): Icon? {
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


    @ApiStatus.Internal
    context(_: KaSession)
    open fun getIcon(ktSymbol: KaSymbol): Icon? {
        // logic copied from org.jetbrains.kotlin.idea.KotlinDescriptorIconProvider
        val declaration = ktSymbol.psi
        return if (declaration?.isValid == true) {
            val isClass = declaration is PsiClass || declaration is KtClass
            val flags = if (isClass) 0 else Iconable.ICON_FLAG_VISIBILITY
            if (declaration is KtDeclaration) {
                // kotlin declaration
                // visibility and abstraction better detect by a descriptor
                getIcon(ktSymbol, flags) ?: declaration.getIcon(flags)
            } else {
                // Use Java icons if it's not Kotlin declaration
                declaration.getIcon(flags)
            }
        } else {
            getIcon(ktSymbol, 0)
        }
    }

    context(_: KaSession)
    private fun getIcon(symbol: KaSymbol, flags: Int): Icon? {
        var result: Icon = getBaseIcon(symbol) ?: return null

        if (flags and Iconable.ICON_FLAG_VISIBILITY > 0) {
            val rowIcon = RowIcon(2)
            rowIcon.setIcon(result, 0)
            rowIcon.setIcon(getVisibilityIcon(symbol), 1)
            result = rowIcon
        }
        return result
    }

    @ApiStatus.Internal
    context(_: KaSession)
    open fun getBaseIcon(symbol: KaSymbol): Icon? {
        val isAbstract = (symbol as? KaDeclarationSymbol)?.modality == KaSymbolModality.ABSTRACT
        return when (symbol) {
            is KaPackageSymbol -> AllIcons.Nodes.Package
            is KaFunctionSymbol -> {
                val isMember = symbol.location == KaSymbolLocation.CLASS
                val isSuspend = (symbol as? KaNamedFunctionSymbol)?.isSuspend == true
                if (isSuspend) {
                    return if (isMember) KotlinIcons.SUSPEND_METHOD else KotlinIcons.SUSPEND_FUNCTION
                }
                val isExtension = symbol.isExtension
                return when {
                    isExtension && isAbstract -> KotlinIcons.ABSTRACT_EXTENSION_FUNCTION
                    isExtension && !isAbstract -> KotlinIcons.EXTENSION_FUNCTION
                    isMember && isAbstract -> PlatformIcons.ABSTRACT_METHOD_ICON
                    isMember && !isAbstract -> IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Method)
                    else -> KotlinIcons.FUNCTION
                }
            }
            is KaClassSymbol -> {
                when (symbol.classKind) {
                    KaClassKind.CLASS -> when {
                        isAbstract -> KotlinIcons.ABSTRACT_CLASS
                        else -> KotlinIcons.CLASS
                    }
                    KaClassKind.ENUM_CLASS -> KotlinIcons.ENUM
                    KaClassKind.ANNOTATION_CLASS -> KotlinIcons.ANNOTATION
                    KaClassKind.INTERFACE -> KotlinIcons.INTERFACE
                    KaClassKind.ANONYMOUS_OBJECT, KaClassKind.OBJECT, KaClassKind.COMPANION_OBJECT -> KotlinIcons.OBJECT
                }
            }
            is KaValueParameterSymbol -> KotlinIcons.PARAMETER
            is KaLocalVariableSymbol -> when {
                symbol.isVal -> KotlinIcons.VAL
                else -> KotlinIcons.VAR
            }
            is KaPropertySymbol -> when {
                symbol.isVal -> KotlinIcons.FIELD_VAL
                else -> KotlinIcons.FIELD_VAR
            }
            is KaTypeParameterSymbol -> IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Class)
            is KaTypeAliasSymbol -> KotlinIcons.TYPE_ALIAS

            else -> {
               LOG.warn("No icon for symbol: $symbol")
                null
            }
        }
    }

    context(_: KaSession)
    private fun getVisibilityIcon(symbol: KaSymbol): Icon? = when ((symbol as? KaDeclarationSymbol)?.visibility) {
        KaSymbolVisibility.PUBLIC -> PlatformIcons.PUBLIC_ICON
        KaSymbolVisibility.PROTECTED,
        KaSymbolVisibility.PACKAGE_PROTECTED,
        KaSymbolVisibility.PACKAGE_PRIVATE,
            -> IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Protected)

        KaSymbolVisibility.PRIVATE -> IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Private)
        KaSymbolVisibility.INTERNAL -> IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Local)
        else -> null
    }



    companion object {
        private val LOG = logger<KotlinIconProvider>()

        private fun createRowIcon(baseIcon: Icon, visibilityIcon: Icon?): RowIcon {
            val rowIcon = RowIcon(2)
            rowIcon.setIcon(baseIcon, 0)
            rowIcon.setIcon(visibilityIcon, 1)
            return rowIcon
        }

        @ApiStatus.Internal
        context(_: KaSession)
        fun getIcon(symbol: KaSymbol): Icon?{
            for (kotlinIconProvider in EXTENSION_POINT_NAME.getIterable().filterIsInstance<KotlinIconProvider>()) {
                kotlinIconProvider.getIcon(symbol)?.let { return it }
            }
            return null
        }

        @ApiStatus.Internal
        context(_: KaSession)
        fun getBaseIcon(symbol: KaSymbol): Icon? {
            for (kotlinIconProvider in EXTENSION_POINT_NAME.getIterable().filterIsInstance<KotlinIconProvider>()) {
                kotlinIconProvider.getBaseIcon(symbol)?.let { return it }
            }
            return null
        }


        @ApiStatus.Internal
        fun getVisibilityIcon(list: KtModifierList?): Icon? {
            for (kotlinIconProvider in EXTENSION_POINT_NAME.getIterable().filterIsInstance<KotlinIconProvider>()) {
                kotlinIconProvider.getVisibilityIcon(list)?.let { return it }
            }
            return null
        }

        private fun PsiFile.scriptIcon(): Icon = when {
            virtualFile.name.endsWith(".gradle.kts") -> GRADLE_SCRIPT
            else -> SCRIPT
        }

       private fun PsiElement.getBaseIcon(): Icon? = when (this) {
            is KtPackageDirective -> AllIcons.Nodes.Package
            is KtFile, is KtLightClassForFacade -> FILE
            is KtScript -> (parent as? KtFile)?.scriptIcon()
            is KtLightClass -> navigationElement.getBaseIcon()
            is KtNamedFunction -> when {
                receiverTypeReference != null ->
                    if (KtPsiUtil.isAbstract(this)) ABSTRACT_EXTENSION_FUNCTION else EXTENSION_FUNCTION
                getStrictParentOfType<KtNamedDeclaration>() is KtClass ->
                    if (KtPsiUtil.isAbstract(this)) {
                        PlatformIcons.ABSTRACT_METHOD_ICON
                    } else {
                        if (this.modifierList?.hasModifier(KtTokens.SUSPEND_KEYWORD) == true) {
                            SUSPEND_METHOD
                        } else {
                            IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Method)
                        }
                    }
                else ->
                    if (this.modifierList?.hasModifier(KtTokens.SUSPEND_KEYWORD) == true) {
                        SUSPEND_FUNCTION
                    } else {
                        FUNCTION
                    }
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

@ApiStatus.Internal
class KotlinNoneIconProvider : KotlinIconProvider() {
    override fun getIcon(psiElement: PsiElement, flags: Int): Icon? = null
    override fun isMatchingExpected(declaration: KtDeclaration): Boolean = false
    context(_: KaSession) override fun getBaseIcon(symbol: KaSymbol): Icon? = null
    context(_: KaSession) override fun getIcon(ktSymbol: KaSymbol): Icon? = null
    override fun getVisibilityIcon(list: KtModifierList?): Icon? = null
}