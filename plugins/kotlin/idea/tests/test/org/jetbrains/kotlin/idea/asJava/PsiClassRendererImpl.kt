// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.asJava

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.asJava.elements.KtLightNullabilityAnnotation
import org.jetbrains.kotlin.asJava.elements.KtLightPsiArrayInitializerMemberValue
import org.jetbrains.kotlin.asJava.elements.KtLightPsiLiteral
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.constants.KClassValue

class PsiClassRendererImpl private constructor(
    renderInner: Boolean,
    membersFilter: MembersFilter) : PsiClassRenderer(renderInner, membersFilter) {
    private fun KtLightPsiLiteral.renderKtLightPsiLiteral(): String {
        val value = value
        if (value is Pair<*, *>) {
            val classId = value.first as? ClassId
            val name = value.second as? Name
            if (classId != null && name != null)
                return "${classId.asSingleFqName()}.${name.asString()}"
        }
        if (value is KClassValue.Value.NormalClass && value.arrayDimensions == 0) {
            return "${value.classId.asSingleFqName()}.class"
        }
        return text
    }

    override fun PsiAnnotationMemberValue.renderAnnotationMemberValue(): String = when (this) {
        is KtLightPsiArrayInitializerMemberValue -> "{${initializers.joinToString { it.renderAnnotationMemberValue() }}}"
        is PsiAnnotation -> renderAnnotation()
        is KtLightPsiLiteral -> renderKtLightPsiLiteral()
        else -> text
    }

    override fun isNullabilityAnnotation(annotation: PsiAnnotation?): Boolean {
        return  annotation is KtLightNullabilityAnnotation<*>
    }

    override fun renderClass(psiClass: PsiClass): String {
        return super.renderClass(psiClass)
    }

    companion object {
        fun renderClass(
            psiClass: PsiClass,
            renderInner: Boolean = false,
            membersFilter: MembersFilter = MembersFilter.DEFAULT
        ): String =
            PsiClassRendererImpl(renderInner, membersFilter).renderClass(psiClass)
    }
}