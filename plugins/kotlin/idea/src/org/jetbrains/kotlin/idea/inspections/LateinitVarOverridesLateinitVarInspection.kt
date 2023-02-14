// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.classOrObjectRecursiveVisitor

import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection

class LateinitVarOverridesLateinitVarInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = classOrObjectRecursiveVisitor(fun(klass) {
        for (declaration in klass.declarations) {
            val property = declaration as? KtProperty ?: continue
            if (!property.hasModifier(KtTokens.OVERRIDE_KEYWORD) || !property.hasModifier(KtTokens.LATEINIT_KEYWORD) || !property.isVar) {
                continue
            }
            val identifier = property.nameIdentifier ?: continue
            val descriptor = property.resolveToDescriptorIfAny() ?: continue
            if (descriptor.overriddenDescriptors.any { (it as? PropertyDescriptor)?.let { d -> d.isLateInit && d.isVar } == true }) {
                holder.registerProblem(
                    identifier,
                    KotlinBundle.message("title.lateinit.var.overrides.lateinit.var")
                )
            }
        }
    })
}
