// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.assignment

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.assignment.plugin.AbstractAssignPluginResolutionAltererExtension
import org.jetbrains.kotlin.idea.compilerPlugin.CachedAnnotationNames
import org.jetbrains.kotlin.idea.compilerPlugin.assignment.ScriptAnnotationNames.Companion.ANNOTATION_OPTION_PREFIX
import org.jetbrains.kotlin.idea.compilerPlugin.getAnnotationNames
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.scripting.definitions.isScript

class IdeAssignPluginResolutionAltererExtension(project: Project) : AbstractAssignPluginResolutionAltererExtension() {

    private val scriptCache = ScriptAnnotationNames(project)
    private val moduleCache = CachedAnnotationNames(project, ANNOTATION_OPTION_PREFIX)

    override fun getAnnotationFqNames(modifierListOwner: KtModifierListOwner?): List<String> {
        if (modifierListOwner == null) return emptyList()
        return if (modifierListOwner.containingFile.isScript()) {
            scriptCache.getNamesForPsiFile(modifierListOwner.containingFile)
        } else {
            moduleCache.getAnnotationNames(modifierListOwner)
        }
    }
}
