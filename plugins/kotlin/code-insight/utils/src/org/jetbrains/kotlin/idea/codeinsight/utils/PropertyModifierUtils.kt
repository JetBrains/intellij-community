// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.idea.base.util.allScope
import org.jetbrains.kotlin.idea.util.application.runWriteActionIfPhysical
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

const val JVM_FIELD_CLASS_ID = "kotlin/jvm/JvmField"
const val JVM_STATIC_CLASS_ID = "kotlin/jvm/JvmStatic"

fun KtProperty.getJvmAnnotations(): List<KtAnnotationEntry> {
    return listOfNotNull(
        findAnnotation(ClassId.fromString(JVM_FIELD_CLASS_ID)),
        findAnnotation(ClassId.fromString(JVM_STATIC_CLASS_ID))
    )
}

fun KtProperty.checkMayBeConstantByFields(): Boolean {
    if (isLocal || isVar || getter != null ||
        hasModifier(KtTokens.CONST_KEYWORD) || hasModifier(KtTokens.OVERRIDE_KEYWORD) || hasActualModifier() ||
        hasDelegate() || receiverTypeReference != null
    ) {
        return false
    }
    val containingClassOrObject = this.containingClassOrObject
    if (!isTopLevel && containingClassOrObject !is KtObjectDeclaration) return false
    return containingClassOrObject?.isObjectLiteral() != true
}

fun replaceReferencesToGetterByReferenceToField(property: KtProperty, fileType: LanguageFileType) {
    val project = property.project
    val javaScope = GlobalSearchScope.getScopeRestrictedByFileTypes(project.allScope(), fileType)
    if (javaScope == GlobalSearchScope.EMPTY_SCOPE) return
    val getter = LightClassUtil.getLightClassPropertyMethods(property).getter ?: return
    val backingField = LightClassUtil.getLightClassPropertyMethods(property).backingField

    if (backingField != null) {
        val getterUsages = ReferencesSearch.search(getter, javaScope).findAll()
        if (getterUsages.isEmpty()) return
        val factory = PsiElementFactory.getInstance(project)
        val fieldFQName = backingField.containingClass!!.qualifiedName + "." + backingField.name

        runWriteActionIfPhysical(property) {
            getterUsages.forEach {
                val call = it.element.getNonStrictParentOfType<PsiMethodCallExpression>()
                if (call != null && it.element == call.methodExpression) {
                    val fieldRef = factory.createExpressionFromText(fieldFQName, it.element)
                    call.replace(fieldRef)
                }
            }
        }
    }
}