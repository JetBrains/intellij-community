// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin

import org.jetbrains.kotlin.idea.core.script.k1.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.util.ClassImportFilter
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractK1ImportsTest : AbstractImportsTest() {
    override fun updateScriptDependencies(psiFile: KtFile) {
        ScriptConfigurationManager.updateScriptDependenciesSynchronously(psiFile)
    }

    override fun registerClassImportFilterExtensions(classImportFilterVetoRegexRules: MutableList<String>) {
        classImportFilterVetoRegexRules.forEach {
            val regex = Regex(".*${it.trim()}.*")
            val filterExtension = ClassImportFilter { classInfo, _ -> !classInfo.fqName.asString().matches(regex) }
            ClassImportFilter.EP_NAME.point.registerExtension(filterExtension, testRootDisposable)
        }
    }
}