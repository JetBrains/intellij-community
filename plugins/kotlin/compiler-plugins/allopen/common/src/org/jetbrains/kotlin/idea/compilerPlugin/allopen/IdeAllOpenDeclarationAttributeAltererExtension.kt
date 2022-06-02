// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.allopen

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.allopen.AbstractAllOpenDeclarationAttributeAltererExtension
import org.jetbrains.kotlin.allopen.AllOpenPluginNames.ANNOTATION_OPTION_NAME
import org.jetbrains.kotlin.allopen.AllOpenPluginNames.PLUGIN_ID
import org.jetbrains.kotlin.idea.compilerPlugin.CachedAnnotationNames
import org.jetbrains.kotlin.idea.compilerPlugin.getAnnotationNames
import org.jetbrains.kotlin.psi.KtModifierListOwner

val ALL_OPEN_ANNOTATION_OPTION_PREFIX = "plugin:$PLUGIN_ID:$ANNOTATION_OPTION_NAME="

class IdeAllOpenDeclarationAttributeAltererExtension(val project: Project) :
    AbstractAllOpenDeclarationAttributeAltererExtension() {

    private val cachedAnnotationsNames = CachedAnnotationNames(project, ALL_OPEN_ANNOTATION_OPTION_PREFIX)

    override fun getAnnotationFqNames(modifierListOwner: KtModifierListOwner?): List<String> {
        return if (ApplicationManager.getApplication().isUnitTestMode) ANNOTATIONS_FOR_TESTS
        else cachedAnnotationsNames.getAnnotationNames(modifierListOwner)
    }
}
