// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.JVM_FIELD_CLASS_ID
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.idea.refactoring.isInterfaceClass
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

internal class MoveMemberOutOfCompanionObjectIntention : MoveMemberIntention(
    textGetter = KotlinBundle.messagePointer("move.out.of.companion.object")
) {
    private fun getClassForCompanionObjectMember(element: KtNamedDeclaration): KtClassOrObject? {
        val container = element.containingClassOrObject
        if (!(container is KtObjectDeclaration && container.isCompanion())) return null
        return container.containingClassOrObject
    }

    override fun getTarget(element: KtNamedDeclaration): K2MoveTargetDescriptor.Declaration<*>? {
        val targetClass = getClassForCompanionObjectMember(element) ?: return null
        return K2MoveTargetDescriptor.ClassOrObject(targetClass)
    }

    override fun applicabilityRange(element: KtNamedDeclaration): TextRange? {
        val containingClassOrObject = getClassForCompanionObjectMember(element) ?: return null
        if (element.findAnnotation(ClassId.fromString(JVM_FIELD_CLASS_ID)) != null) return null
        if (containingClassOrObject.isInterfaceClass()) return null
        return element.nameIdentifier?.textRange
    }
}