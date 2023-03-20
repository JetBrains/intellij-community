// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.presentation

import com.intellij.ide.IconProvider
import com.intellij.navigation.ColoredItemPresentation
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.ItemPresentationProvider
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.Iconable
import com.intellij.ui.ExperimentalUI
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.KotlinIconProvider
import org.jetbrains.kotlin.lexer.KtTokens
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

    override fun getPresentableText() = declaration.name

    override fun getLocationString(): String? {
        if ((declaration is KtFunction && declaration.isLocal) || (declaration is KtClassOrObject && declaration.isLocal)) {
            val containingDeclaration = declaration.getStrictParentOfType<KtNamedDeclaration>() ?: return null
            val containerName = containingDeclaration.fqName ?: containingDeclaration.name
            return getPresentationInContainer(containerName.toString())
        }

        val name = declaration.fqName
        val parent = declaration.parent
        val containerText = if (name != null) {
            val qualifiedContainer = name.parent().toString()
            if (parent is KtFile && declaration.hasModifier(KtTokens.PRIVATE_KEYWORD)) {
                KotlinBundle.message("presentation.text.in.container", parent.name, qualifiedContainer)
            } else {
                qualifiedContainer
            }
        } else {
            getNameForContainingObjectLiteral() ?: return null
        }

        val receiverTypeRef = (declaration as? KtCallableDeclaration)?.receiverTypeReference
        return when {
            receiverTypeRef != null -> {
                getPresentationTextForReceiver(receiverTypeRef.text, containerText)
            }
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
        for (kotlinIconProvider in IconProvider.EXTENSION_POINT_NAME.extensionList.filterIsInstance<KotlinIconProvider>()) {
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
            name?.let { append(it) }

            append("(")
            append(function.valueParameters.joinToString {
                (if (it.isVarArg) "vararg " else "") + (it.typeReference?.text ?: "")
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

private fun getPresentationInContainer(param: Any): String {
    if (ExperimentalUI.isNewUI()) {
        return KotlinBundle.message("presentation.text.in.container.paren.no.brackets", param)
    } else {
        return KotlinBundle.message("presentation.text.in.container.paren", param)
    }
}

private fun getPresentationText(param: Any): String {
    if (ExperimentalUI.isNewUI()) {
        return KotlinBundle.message("presentation.text.paren.no.brackets", param)
    } else {
        return KotlinBundle.message("presentation.text.paren", param)
    }
}

private fun getPresentationTextForReceiver(vararg params: Any): String {
    if (ExperimentalUI.isNewUI()) {
        return KotlinBundle.message("presentation.text.for.receiver.in.container.paren.no.brackets", *params)
    } else {
        return KotlinBundle.message("presentation.text.for.receiver.in.container.paren", *params)
    }
}

