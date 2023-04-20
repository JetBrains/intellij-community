// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fe10.codeInsight.tooling

import org.jetbrains.kotlin.base.fe10.analysis.classId
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptorWithResolutionScopes
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinTestAvailabilityChecker
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.AbstractGenericTestIconProvider
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.resolve.descriptorUtil.getAllSuperClassifiers

object Fe10GenericTestIconProvider : AbstractGenericTestIconProvider() {
    override fun isKotlinTestDeclaration(declaration: KtClassOrObject): Boolean {
        val descriptor = declaration.resolveToDescriptorIfAny() ?: return false
        return descriptor.getAllSuperClassifiers().any {
            isKotlinTestDeclaration(it)
        }
    }

    private tailrec fun isIgnored(descriptor: DeclarationDescriptor): Boolean {
        if (descriptor.annotations.any { it.classId == KotlinTestAvailabilityChecker.IGNORE_FQ_NAME }) {
            return true
        }

        val containingClass = descriptor.containingDeclaration as? ClassDescriptor ?: return false
        return isIgnored(containingClass)
    }

    internal fun isKotlinTestDeclaration(descriptor: DeclarationDescriptor): Boolean {
        if (isIgnored(descriptor)) {
            return false
        }

        if (descriptor.annotations.any { it.classId == KotlinTestAvailabilityChecker.TEST_FQ_NAME }) {
            return true
        }

        val classDescriptor = descriptor as? ClassDescriptorWithResolutionScopes ?: return false
        return classDescriptor.declaredCallableMembers.any { isKotlinTestDeclaration(it) }
    }
}