// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.noarg

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.compilerPlugin.CachedAnnotationNames
import org.jetbrains.kotlin.idea.compilerPlugin.getAnnotationNames
import org.jetbrains.kotlin.noarg.NoArgPluginNames.PLUGIN_ID
import org.jetbrains.kotlin.noarg.NoArgPluginNames.ANNOTATION_OPTION_NAME
import org.jetbrains.kotlin.noarg.diagnostic.AbstractNoArgDeclarationChecker
import org.jetbrains.kotlin.psi.KtModifierListOwner

val NO_ARG_ANNOTATION_OPTION_PREFIX =
    "plugin:$PLUGIN_ID:$ANNOTATION_OPTION_NAME="

class IdeNoArgDeclarationChecker(project: Project) : AbstractNoArgDeclarationChecker(false) {

    private val cachedAnnotationNames = CachedAnnotationNames(project, NO_ARG_ANNOTATION_OPTION_PREFIX)

    override fun getAnnotationFqNames(modifierListOwner: KtModifierListOwner?): List<String> =
        cachedAnnotationNames.getAnnotationNames(modifierListOwner)
}
