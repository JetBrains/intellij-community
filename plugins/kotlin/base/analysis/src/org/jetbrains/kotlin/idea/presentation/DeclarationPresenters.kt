// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.presentation

import com.intellij.ide.IconProvider
import com.intellij.navigation.ColoredItemPresentation
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.ItemPresentationProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ExperimentalUI
import org.jetbrains.kotlin.idea.KotlinIconProvider
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import javax.swing.Icon

open class KotlinDefaultNamedDeclarationPresentation(private val declaration: KtNamedDeclaration) : ColoredItemPresentation {

    override fun getTextAttributesKey(): TextAttributesKey? {
        if (KtPsiUtil.isDeprecated(declaration)) {
            return CodeInsightColors.DEPRECATED_ATTRIBUTES
        }
        return null
    }

    override fun getPresentableText(): @NlsSafe String? {
        val name = declaration.name
        if (declaration is KtCallableDeclaration && name != null) {
            declaration.receiverTypeReference?.getTypeText()?.let {
              return StringUtil.getQualifiedName(StringUtil.getShortName(it), name)
          }
        }
        return name
    }

    override fun getLocationString(): String? {
        if ((declaration is KtFunction && declaration.isLocal) || (declaration is KtClassOrObject && declaration.isLocal)) {
            val containingDeclaration = declaration.getStrictParentOfType<KtNamedDeclaration>() ?: return null
            val containerName = containingDeclaration.fqName ?: containingDeclaration.name ?: return null
            return getPresentationInContainer(containerName.toString())
        }

        val name = declaration.fqName
        val parent = declaration.parent
        val containerText = if (name != null) {
            val qualifiedContainer = name.parent().takeUnless { it == FqName.ROOT }?.toString() ?: return null
            if (parent is KtFile && declaration.hasModifier(KtTokens.PRIVATE_KEYWORD)) {
                KotlinBundle.message("presentation.text.in.container", parent.name, qualifiedContainer)
            } else {
                qualifiedContainer
            }
        } else {
            getNameForContainingObjectLiteral() ?: return null
        }

        return when {
            parent is KtFile -> getPresentationText(containerText)
            else -> getPresentationInContainer(containerText)
        }
    }

    private fun getNameForContainingObjectLiteral(): String? {
        val objectLiteral = declaration.getStrictParentOfType<KtObjectLiteralExpression>() ?: return null
        val container = objectLiteral.getStrictParentOfType<KtNamedDeclaration>() ?: return null
        val containerFqName = container.fqName?.asString() ?: return null
        return KotlinBundle.message("presentation.text.object.in.container", containerFqName)
    }

    override fun getIcon(unused: Boolean): Icon? {
        for (kotlinIconProvider in IconProvider.EXTENSION_POINT_NAME.getIterable().filterIsInstance<KotlinIconProvider>()) {
            kotlinIconProvider.getIcon(declaration, Iconable.ICON_FLAG_VISIBILITY or Iconable.ICON_FLAG_READ_STATUS)?.let { return it }
        }
        return null
    }
}

class KtDefaultDeclarationPresenter : ItemPresentationProvider<KtNamedDeclaration> {
    override fun getPresentation(item: KtNamedDeclaration) = KotlinDefaultNamedDeclarationPresentation(item)
}

open class KotlinFunctionPresentation(
    private val function: KtFunction,
    private val name: String? = function.name
) : KotlinDefaultNamedDeclarationPresentation(function) {
    override fun getPresentableText(): String {
        return buildString {
            function.receiverTypeReference?.getTypeText()?.let {
                append(StringUtil.getShortName(it))
                append(".")
            }

            name?.let { append(it) }

            append("(")
            append(function.valueParameters.joinToString {
                (if (it.isVarArg) "vararg " else "") + StringUtil.getShortName(it.typeReference?.getTypeText() ?: "")
            })
            append(")")
        }
    }

    override fun getLocationString(): String? {
        if (function is KtConstructor<*>) {
            val name = function.getContainingClassOrObject().fqName ?: return null
            return getPresentationInContainer(name)
        }

        return super.getLocationString()
    }
}

class KtFunctionPresenter : ItemPresentationProvider<KtFunction> {
    override fun getPresentation(function: KtFunction): ItemPresentation? {
        if (function is KtFunctionLiteral) return null

        return KotlinFunctionPresentation(function)
    }
}

internal fun getPresentationInContainer(param: Any): String {
    if (ExperimentalUI.isNewUI() && !ApplicationManager.getApplication().isUnitTestMode) {
        return KotlinBundle.message("presentation.text.in.container.paren.no.brackets", param)
    } else {
        return KotlinBundle.message("presentation.text.in.container.paren", param)
    }
}

internal fun getPresentationText(param: Any): String {
    if (ExperimentalUI.isNewUI() && !ApplicationManager.getApplication().isUnitTestMode) {
        return KotlinBundle.message("presentation.text.paren.no.brackets", param)
    } else {
        return KotlinBundle.message("presentation.text.paren", param)
    }
}
