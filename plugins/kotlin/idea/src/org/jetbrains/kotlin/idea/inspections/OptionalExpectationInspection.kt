// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.caches.project.implementingDescriptors
import org.jetbrains.kotlin.idea.caches.resolve.findModuleDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.quickfix.expectactual.CreateActualClassFix
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.oldFashionedDescription
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.classOrObjectVisitor
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier
import org.jetbrains.kotlin.resolve.checkers.ExpectedActualDeclarationChecker.Companion.allStrongIncompatibilities
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.multiplatform.ExpectActualCompatibility
import org.jetbrains.kotlin.resolve.multiplatform.ExpectedActualResolver
import org.jetbrains.kotlin.resolve.multiplatform.OptionalAnnotationUtil
import org.jetbrains.kotlin.resolve.multiplatform.onlyFromThisModule

import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection

class OptionalExpectationInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return classOrObjectVisitor(fun(classOrObject: KtClassOrObject) {
            if (classOrObject !is KtClass || !classOrObject.isAnnotation()) return
            if (!classOrObject.hasExpectModifier()) return

            val descriptor = classOrObject.resolveToDescriptorIfAny() ?: return
            if (!descriptor.annotations.hasAnnotation(OptionalAnnotationUtil.OPTIONAL_EXPECTATION_FQ_NAME)) return

            // FIXME(dsavvinov): this is wrong in HMPP model, use logic similar to ExpectedActualDeclarationChecker
            val implementingModules = classOrObject.findModuleDescriptor().implementingDescriptors
            if (implementingModules.isEmpty()) return

            for (actualModuleDescriptor in implementingModules) {
                val compatibility = ExpectedActualResolver.findActualForExpected(
                    descriptor, actualModuleDescriptor, onlyFromThisModule(actualModuleDescriptor)
                ) ?: continue

                if (!compatibility.allStrongIncompatibilities() &&
                    (ExpectActualCompatibility.Compatible in compatibility ||
                            !compatibility.values.flatMapTo(
                                hashSetOf()
                            ) { it }.all { actual ->
                                val expectedOnes = ExpectedActualResolver.findExpectedForActual(
                                    actual, onlyFromThisModule(descriptor.module)
                                )
                                expectedOnes != null && ExpectActualCompatibility.Compatible in expectedOnes.keys
                            })
                ) continue
                val platform = actualModuleDescriptor.platform ?: continue
                if (platform.isCommon()) continue

                val displayedName = actualModuleDescriptor.getCapability(ModuleInfo.Capability)?.displayedName ?: ""
                val actualModule = (actualModuleDescriptor.getCapability(ModuleInfo.Capability) as? ModuleSourceInfo)?.module ?: continue
                holder.registerProblem(
                    classOrObject.nameIdentifier ?: classOrObject,
                    KotlinBundle.message(
                        "optionally.expected.annotation.has.no.actual.annotation.in.module.0.for.platform.1",
                        displayedName,
                        platform.oldFashionedDescription
                    ),
                    IntentionWrapper(CreateActualClassFix(classOrObject, actualModule, platform))
                )
            }
        })
    }
}
