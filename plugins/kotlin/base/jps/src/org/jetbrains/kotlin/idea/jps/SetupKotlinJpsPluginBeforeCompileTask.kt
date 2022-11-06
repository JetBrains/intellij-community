// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jps

import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.compiler.CompileTask
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.project.stateStore
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.config.SettingConstants
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinArtifactsDownloader
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import com.intellij.openapi.application.runReadAction

class SetupKotlinJpsPluginBeforeCompileTask : CompileTask {
    override fun execute(context: CompileContext): Boolean {
        val project = context.project
        val jpsVersion = KotlinJpsPluginSettings.supportedJpsVersion(
            project = project,
            onUnsupportedVersion = { context.addErrorWithReferenceToKotlincXml(it) }
        ) ?: return true

        return KotlinArtifactsDownloader.lazyDownloadMissingJpsPluginDependencies(
            project = project,
            jpsVersion = jpsVersion,
            indicator = context.progressIndicator,
            onError = { context.addError(it) }
        )
    }

    private fun CompileContext.addError(@Nls(capitalization = Nls.Capitalization.Sentence) msg: String) =
        addMessage(CompilerMessageCategory.ERROR, msg, null, -1, -1)

    private fun CompileContext.addErrorWithReferenceToKotlincXml(@Nls(capitalization = Nls.Capitalization.Sentence) msg: String) {
        val virtualFile = project.stateStore
            .directoryStorePath
            ?.resolve(SettingConstants.KOTLIN_COMPILER_SETTINGS_FILE)
            ?.let(VirtualFileManager.getInstance()::findFileByNioPath)

        val psiFile = runReadAction { virtualFile?.toPsiFile(project) }
        addMessage(CompilerMessageCategory.ERROR, msg, virtualFile?.url, -1, -1, psiFile)
    }
}
