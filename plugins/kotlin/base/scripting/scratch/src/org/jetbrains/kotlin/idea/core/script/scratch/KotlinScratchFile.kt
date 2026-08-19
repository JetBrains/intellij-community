// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.scratch

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.core.script.k2.configurations.KotlinScriptService
import org.jetbrains.kotlin.idea.core.script.v1.ScratchFileOptionsByFile

class KotlinScratchFile(project: Project, virtualFile: VirtualFile, val coroutineScope: CoroutineScope) :
    ScratchFile(project, virtualFile) {
    val executor: KotlinScratchExecutor = KotlinScratchExecutor(this, project, coroutineScope)

    override fun setModule(module: Module?) {
        ScratchFileOptionsByFile.update(project, virtualFile) {
            copy(selectedModule = module?.name)
        }

        reloadConfiguration()
    }

    override fun selectJdk(jdk: Sdk) {
        saveOptions { copy(selectedJdkHome = jdk.homePath) }
        reloadConfiguration()
    }

    private fun reloadConfiguration() {
        coroutineScope.launch {
            KotlinScriptService.getInstance(project).reload(virtualFile)
        }
    }
}