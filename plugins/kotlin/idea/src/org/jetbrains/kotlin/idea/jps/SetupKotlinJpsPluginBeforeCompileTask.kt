// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.jps

import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.compiler.CompileTask
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.project.stateStore
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.config.SettingConstants
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinArtifactsDownloader
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.core.util.toPsiFile

class SetupKotlinJpsPluginBeforeCompileTask : CompileTask {
    override fun execute(context: CompileContext): Boolean {
        val version = KotlinJpsPluginSettings.getInstance(context.project)?.settings?.version ?: return true

        val parsed = IdeKotlinVersion.opt(version)
        if (parsed == null) {
            context.addErrorWithReferenceToKotlincXml(
                KotlinBundle.message(
                    "failed.to.parse.kotlin.version.0.from.1",
                    version,
                    SettingConstants.KOTLIN_COMPILER_SETTINGS_FILE
                )
            )
            return false
        }
        if (parsed.kotlinVersion < jpsMinimumSupportedVersion) {
            context.addErrorWithReferenceToKotlincXml(
                KotlinBundle.message(
                    "kotlin.jps.compiler.minimum.supported.version.not.satisfied",
                    jpsMinimumSupportedVersion,
                    version
                )
            )
            return false
        }

        val jpsPluginClassPathJar = KotlinArtifactsDownloader.lazyDownloadMavenArtifact(
            context.project,
            KotlinArtifacts.KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID,
            version,
            context.progressIndicator,
            KotlinBundle.message("progress.text.downloading.kotlin.jps.plugin"),
            onError = { context.addError(it) }
        )
        if (jpsPluginClassPathJar == null) {
            return false
        }

        val unpackedKotlinc = KotlinArtifactsDownloader.lazyDownloadAndUnpackKotlincDist(
            context.project,
            version,
            context.progressIndicator,
            onError = { context.addError(it) },
        )
        if (unpackedKotlinc == null) {
            return false
        }

        return true
    }

    private fun CompileContext.addError(@Nls(capitalization = Nls.Capitalization.Sentence) msg: String) =
        addMessage(CompilerMessageCategory.ERROR, msg, null, -1, -1)

    private fun CompileContext.addErrorWithReferenceToKotlincXml(@Nls(capitalization = Nls.Capitalization.Sentence) msg: String) {
        val virtualFile = project.stateStore.directoryStorePath?.resolve(SettingConstants.KOTLIN_COMPILER_SETTINGS_FILE)
            ?.let { VirtualFileManager.getInstance().findFileByNioPath(it) }
        addMessage(CompilerMessageCategory.ERROR, msg, virtualFile?.url, -1, -1, virtualFile?.toPsiFile(project))
    }

    companion object {
        @JvmStatic
        val jpsMinimumSupportedVersion = IdeKotlinVersion.get("1.5.10").kotlinVersion
    }
}
