// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.assignment

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.assignment.plugin.AbstractAssignPluginResolutionAltererExtension
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.scripting.definitions.isScript

class IdeAssignPluginResolutionAltererExtension(private val project: Project) : AbstractAssignPluginResolutionAltererExtension() {
    override fun getAnnotationFqNames(modifierListOwner: KtModifierListOwner?): List<String> {
        if (modifierListOwner == null) {
            return emptyList()
        }

        val cache = project.service<AssignmentAnnotationNamesCache>()

        if (modifierListOwner.containingFile.isScript()) {
            return cache.getNamesForPsiFile(modifierListOwner.containingFile)
        } else {
            val module = modifierListOwner.module ?: return emptyList()
            return cache.getNamesForModule(module)
        }
    }
}
