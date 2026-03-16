// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.findUsages

import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.core.script.k1.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.test.Diagnostic
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractK1KotlinScriptFindUsagesTest : AbstractKotlinScriptFindUsagesTest() {
    override fun getDiagnosticProvider(): (KtFile) -> List<Diagnostic> {
        return k1DiagnosticProviderForFindUsages()
    }

    override fun updateScripts(file: PsiFile) {
        ScriptConfigurationManager.updateScriptDependenciesSynchronously(file)
    }
}

class Foo
fun Foo.bar() = println("Hello")