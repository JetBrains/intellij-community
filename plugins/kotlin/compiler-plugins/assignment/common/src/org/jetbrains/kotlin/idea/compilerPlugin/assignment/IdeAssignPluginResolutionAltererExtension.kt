// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.assignment

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.assignment.plugin.AbstractAssignPluginResolutionAltererExtension
import org.jetbrains.kotlin.assignment.plugin.AssignmentPluginNames.ANNOTATION_OPTION_NAME
import org.jetbrains.kotlin.assignment.plugin.AssignmentPluginNames.PLUGIN_ID
import org.jetbrains.kotlin.idea.compilerPlugin.CachedAnnotationNames
import org.jetbrains.kotlin.idea.compilerPlugin.getAnnotationNames
import org.jetbrains.kotlin.psi.KtModifierListOwner

class IdeAssignPluginResolutionAltererExtension(project: Project) : AbstractAssignPluginResolutionAltererExtension() {

    private companion object {
        const val ANNOTATION_OPTION_PREFIX = "plugin:$PLUGIN_ID:$ANNOTATION_OPTION_NAME="
    }

    private val cachedAnnotationNames = CachedAnnotationNames(project, ANNOTATION_OPTION_PREFIX)

    override fun getAnnotationFqNames(modifierListOwner: KtModifierListOwner?): List<String> =
        cachedAnnotationNames.getAnnotationNames(modifierListOwner)
}
