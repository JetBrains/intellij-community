// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.noarg.ide

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.annotation.plugin.ide.CachedAnnotationNames
import org.jetbrains.kotlin.annotation.plugin.ide.getAnnotationNames
import org.jetbrains.kotlin.noarg.NoArgCommandLineProcessor
import org.jetbrains.kotlin.noarg.diagnostic.AbstractNoArgDeclarationChecker
import org.jetbrains.kotlin.psi.KtModifierListOwner

internal val NO_ARG_ANNOTATION_OPTION_PREFIX =
    "plugin:${NoArgCommandLineProcessor.PLUGIN_ID}:${NoArgCommandLineProcessor.ANNOTATION_OPTION.optionName}="

class IdeNoArgDeclarationChecker(project: Project) : AbstractNoArgDeclarationChecker(false) {

    private val cachedAnnotationNames = CachedAnnotationNames(project, NO_ARG_ANNOTATION_OPTION_PREFIX)

    override fun getAnnotationFqNames(modifierListOwner: KtModifierListOwner?): List<String> =
        cachedAnnotationNames.getAnnotationNames(modifierListOwner)
}
