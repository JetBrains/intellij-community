// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.libraries

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.scopes.ModulesScope
import com.intellij.psi.JavaPsiFacade

internal class AddKotlinCoroutinesQuickFixProvider : SimpleAddKotlinLibraryQuickFixProvider(
    libraryGroupId = "org.jetbrains.kotlinx",
    libraryArtifactId = "kotlinx-coroutines-core",
    namesToCheck = setOf("runBlocking", "CoroutineScope")
) {
    override fun hasLibrary(module: Module): Boolean {
        val scope = ModulesScope.moduleWithDependenciesAndLibrariesScope(module)
        return JavaPsiFacade.getInstance(module.project).findClasses("kotlinx.coroutines.CoroutineScope", scope).isNotEmpty()
    }
}