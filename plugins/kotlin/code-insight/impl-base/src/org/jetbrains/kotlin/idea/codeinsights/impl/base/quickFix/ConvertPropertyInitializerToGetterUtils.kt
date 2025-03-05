// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration

fun canConvertPropertyInitializerToGetterByPsi(element: KtProperty): Boolean {
    return (element.getter == null &&
            !element.isExtensionDeclaration() &&
            !element.isLocal &&
            element.findAnnotation(ClassId.topLevel(JvmAbi.JVM_FIELD_ANNOTATION_FQ_NAME)) == null &&
            !element.hasModifier(KtTokens.CONST_KEYWORD))
}

fun KtExpression.hasReferenceToPrimaryConstructorParameter(): Boolean {
    val primaryConstructorParameters = containingClass()?.primaryConstructor?.valueParameters.orEmpty()
        .filterNot { it.hasValOrVar() }.associateBy { it.name }.ifEmpty { return false }

    return anyDescendantOfType<KtNameReferenceExpression> {
        val parameter = primaryConstructorParameters[it.text]
        parameter != null && parameter == it.mainReference.resolve()
    }
}

fun convertPropertyInitializerToGetter(
    project: Project,
    element: KtProperty,
    typeInfo: CallableReturnTypeUpdaterUtils.TypeInfo,
    updater: ModPsiUpdater,
) {
    convertPropertyInitializerToGetterInner(element) {
        if (!typeInfo.defaultType.isUnit) {
            CallableReturnTypeUpdaterUtils.updateType(element, typeInfo, project, updater)
        }
    }
}

fun convertPropertyInitializerToGetterInner(element: KtProperty, typeUpdater: () -> Unit) {
    val psiFactory = KtPsiFactory(element.project)

    val initializer = element.initializer ?: return
    val getter = psiFactory.createPropertyGetter(initializer)
    val setter = element.setter

    when {
        setter != null -> element.addBefore(getter, setter)
        element.isVar -> {
            element.add(getter)
            val notImplemented = psiFactory.createExpression("TODO()")
            val notImplementedSetter = psiFactory.createPropertySetter(notImplemented)
            element.add(notImplementedSetter)
        }

        else -> element.add(getter)
    }

    element.initializer = null
    typeUpdater()
}
