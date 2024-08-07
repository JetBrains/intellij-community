// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.script.k2

import com.intellij.openapi.module.Module
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import org.jetbrains.kotlin.idea.base.projectStructure.NewKotlinFileHook
import org.jetbrains.kotlin.idea.base.scripting.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.k2.BaseScriptModel
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class NewMainKtsFileHook : NewKotlinFileHook() {
    override fun postProcess(createdElement: KtFile, module: Module) {
        val virtualFile = createdElement.virtualFile
        val ktFile = createdElement.safeAs<KtFile>() ?: return
        if (!isMainKtsScript(virtualFile) || !ktFile.isScript()) return

        val project = ktFile.project

        runWithModalProgressBlocking(
            project,
            KotlinBaseScriptingBundle.message("progress.title.loading.script.dependencies")
        ) {
            MainKtsScriptDependenciesSource.getInstance(project)?.updateDependenciesAndCreateModules(
                listOf(BaseScriptModel(virtualFile))
            )
        }
    }
}
