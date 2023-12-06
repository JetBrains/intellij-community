// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fe10.codeInsight.tooling

import org.jetbrains.kotlin.base.fe10.analysis.classId
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinTestAvailabilityChecker
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.AbstractGenericTestIconProvider
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.resolve.descriptorUtil.getAllSuperClassifiers

object Fe10GenericTestIconProvider : AbstractGenericTestIconProvider() {
    override fun isKotlinTestDeclaration(declaration: KtNamedDeclaration): Boolean =
      when (declaration) {
          is KtClassOrObject -> declaration.resolveToDescriptorIfAny()?.getAllSuperClassifiers()?.any(::isKotlinTestDeclaration)
          is KtNamedFunction -> declaration.resolveToDescriptorIfAny()?.let(::isKotlinTestDeclaration)
          else -> false
      } ?: false

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

        if ((descriptor as? DeclarationDescriptorWithVisibility)?.visibility != DescriptorVisibilities.PUBLIC) {
            return false
        }

        if (descriptor.annotations.any { it.classId == KotlinTestAvailabilityChecker.TEST_FQ_NAME }) {
            return true
        }

        val classDescriptor = descriptor as? ClassDescriptorWithResolutionScopes ?: return false
        return classDescriptor.declaredCallableMembers.any { isKotlinTestDeclaration(it) }
    }
}