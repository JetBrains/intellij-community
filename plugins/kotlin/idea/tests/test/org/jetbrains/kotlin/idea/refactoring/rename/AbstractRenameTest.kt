// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithAllCompilerChecks
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils
import org.jetbrains.kotlin.idea.test.k1DiagnosticsProvider
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils

abstract class AbstractK1RenameTest : AbstractRenameTest() {
    override fun findPsiDeclarationToRename(
        contextFile: KtFile,
        target: KotlinTarget
    ): PsiElement {
        val module = contextFile.analyzeWithAllCompilerChecks().moduleDescriptor

        val descriptor = when (target) {
            is KotlinTarget.Classifier -> module.findClassAcrossModuleDependencies(target.classId)!!

            is KotlinTarget.Callable -> {
                val callableId = target.callableId

                val targetScope = callableId.classId
                    ?.let { classId -> module.findClassAcrossModuleDependencies(classId)!!.defaultType.memberScope }
                    ?: module.getPackage(callableId.packageName).memberScope

                when (target.type) {
                    KotlinTarget.CallableType.FUNCTION -> targetScope.getContributedFunctions(
                        callableId.callableName,
                        NoLookupLocation.FROM_TEST
                    ).first()

                    KotlinTarget.CallableType.PROPERTY -> targetScope.getContributedVariables(
                        callableId.callableName,
                        NoLookupLocation.FROM_TEST
                    ).first()
                }
            }

            is KotlinTarget.EnumEntry -> module.findClassAcrossModuleDependencies(target.classId)!!
        }

        return DescriptorToSourceUtils.descriptorToDeclaration(descriptor)!!
    }

    override fun checkForUnexpectedErrors(ktFile: KtFile) {
        DirectiveBasedActionUtils.checkForUnexpectedErrors(ktFile, DirectiveBasedActionUtils.ERROR_DIRECTIVE, k1DiagnosticsProvider)
    }
}

