// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.presentation

import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.ItemPresentationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.*
import javax.swing.Icon

class KtJvmNameAnnotationPresenter : ItemPresentationProvider<KtAnnotationEntry> {
    override fun getPresentation(annotationEntry: KtAnnotationEntry): ItemPresentation? {
        if (annotationEntry.shortName?.asString() != JvmFileClassUtil.JVM_NAME_SHORT) return null

        return annotationEntry.parent.parent.toItemPresentation(annotationEntry)
    }

    private fun PsiElement.toItemPresentation(annotationEntry: KtAnnotationEntry): ItemPresentation? {
        return when (this) {
            is KtFile -> KtJvmNameAnnotatedFilePresentation(annotationEntry)
            is KtFunction -> KotlinFunctionPresentation(this, JvmFileClassUtil.getLiteralStringFromAnnotation(annotationEntry))
            is KtNamedDeclaration -> getDeclarationPresentation(this, annotationEntry)
            is KtPropertyAccessor -> {
                val property = this.parentOfType<KtProperty>() ?: return null
                getDeclarationPresentation(property, annotationEntry)
            }
            is KtFileAnnotationList -> parent.toItemPresentation(annotationEntry)
            else -> null
        }
    }

    private fun getDeclarationPresentation(declaration: KtNamedDeclaration, annotationEntry: KtAnnotationEntry): ItemPresentation {
        return object : KotlinDefaultNamedDeclarationPresentation(declaration) {
            override fun getPresentableText(): String? = JvmFileClassUtil.getLiteralStringFromAnnotation(annotationEntry)
        }
    }
}

class KtJvmNameAnnotatedFilePresentation(private val annotationEntry: KtAnnotationEntry) : ItemPresentation {
    private val containingFile = annotationEntry.containingKtFile

    override fun getPresentableText(): String? = JvmFileClassUtil.getLiteralStringFromAnnotation(annotationEntry)

    override fun getLocationString(): String {
        return KotlinBundle.message("presentation.text.in.container", containingFile.name, containingFile.packageFqName)
    }

    override fun getIcon(unused: Boolean): Icon = KotlinIcons.FILE
}
