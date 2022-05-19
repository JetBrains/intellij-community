// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jps

import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.compiler.CompileTask
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.pom.Navigatable
import com.intellij.project.stateStore
import com.intellij.psi.util.descendants
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.SettingConstants
import org.jetbrains.kotlin.config.toKotlinVersion
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinArtifactsDownloader
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.util.application.runReadAction

class SetupKotlinJpsPluginBeforeCompileTask : CompileTask {
    override fun execute(context: CompileContext): Boolean {
        val jpsVersion = jpsVersion(context) ?: return true
        KotlinArtifactsDownloader.lazyDownloadMavenArtifact(
            context.project,
            KotlinArtifacts.KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID,
            jpsVersion,
            context.progressIndicator,
            KotlinBundle.message("progress.text.downloading.kotlin.jps.plugin"),
            onError = { context.addError(it) }
        ) ?: return false

        KotlinArtifactsDownloader.lazyDownloadAndUnpackKotlincDist(
            context.project,
            jpsVersion,
            context.progressIndicator,
            onError = { context.addError(it) },
        ) ?: return false

        return true
    }

    private fun jpsVersion(context: CompileContext): String? {
        val version = KotlinJpsPluginSettings.jpsVersion(context.project) ?: return null

        val parsedKotlinVersion = IdeKotlinVersion.opt(version)?.kotlinVersion
        if (parsedKotlinVersion == null) {
            context.addErrorWithReferenceToKotlincXml(
                KotlinBundle.message(
                    "failed.to.parse.kotlin.version.0.from.1",
                    version,
                    SettingConstants.KOTLIN_COMPILER_SETTINGS_FILE,
                ),
                version,
            )

            return null
        }

        if (parsedKotlinVersion < jpsMinimumSupportedVersion) {
            context.addErrorWithReferenceToKotlincXml(
                KotlinBundle.message(
                    "kotlin.jps.compiler.minimum.supported.version.not.satisfied",
                    jpsMinimumSupportedVersion,
                    version,
                ),
                version,
            )

            return null
        }

        if (parsedKotlinVersion > jpsMaximumSupportedVersion) {
            context.addErrorWithReferenceToKotlincXml(
                KotlinBundle.message(
                    "kotlin.jps.compiler.maximum.supported.version.not.satisfied",
                    jpsMaximumSupportedVersion,
                    version,
                ),
                version,
            )

            return null
        }

        return version
    }

    private fun CompileContext.addError(@Nls(capitalization = Nls.Capitalization.Sentence) msg: String) =
        addMessage(CompilerMessageCategory.ERROR, msg, null, -1, -1)

    private fun CompileContext.addErrorWithReferenceToKotlincXml(
        @Nls(capitalization = Nls.Capitalization.Sentence) msg: String,
        version: String
    ) {
        val virtualFile = project.stateStore
            .directoryStorePath
            ?.resolve(SettingConstants.KOTLIN_COMPILER_SETTINGS_FILE)
            ?.let(VirtualFileManager.getInstance()::findFileByNioPath)

        val psiFile = runReadAction { virtualFile?.toPsiFile(project) }
        val elementWithError = psiFile?.descendants()?.find { it.textMatches(version) } as? Navigatable
        addMessage(CompilerMessageCategory.ERROR, msg, virtualFile?.url, -1, -1, elementWithError ?: psiFile)
    }

    companion object {
        @JvmStatic
        val jpsMinimumSupportedVersion: KotlinVersion = IdeKotlinVersion.get("1.5.10").kotlinVersion

        @JvmStatic
        val jpsMaximumSupportedVersion: KotlinVersion = LanguageVersion.values().last().toKotlinVersion()
    }
}
