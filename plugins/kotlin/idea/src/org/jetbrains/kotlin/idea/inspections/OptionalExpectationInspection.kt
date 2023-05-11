// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analyzer.moduleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.project.implementingDescriptors
import org.jetbrains.kotlin.idea.caches.resolve.findModuleDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.quickfix.expectactual.CreateMissedActualsFix
import org.jetbrains.kotlin.idea.util.actualsForExpected
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.classOrObjectVisitor
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.multiplatform.OptionalAnnotationUtil

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

            val actualizedLeafModules = descriptor.actualsForExpected()
                .flatMap { it.module.implementingDescriptors.plus(it.module) } //all actualized modules
                .filter { it.implementingDescriptors.isEmpty() } //only leafs
                .toSet()
            val notActualizedLeafModules = implementingModules
                .filter { it.implementingDescriptors.isEmpty() && it !in actualizedLeafModules }
                .mapNotNull { (it.moduleInfo as? ModuleSourceInfo)?.module }
                .toSet()
            if (notActualizedLeafModules.isEmpty()) return
            holder.registerProblem(
                classOrObject.nameIdentifier ?: classOrObject,
                KotlinBundle.message("fix.create.missing.actual.declarations"),
                IntentionWrapper(CreateMissedActualsFix(classOrObject, notActualizedLeafModules))
            )
        })
    }
}
