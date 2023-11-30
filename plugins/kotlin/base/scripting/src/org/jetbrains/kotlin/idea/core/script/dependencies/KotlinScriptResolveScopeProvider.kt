// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.script.dependencies

import com.intellij.ide.IdeBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.NonPhysicalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem
import com.intellij.psi.PsiManager
import com.intellij.psi.ResolveScopeProvider
import com.intellij.psi.search.DelegatingGlobalSearchScope
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfoOrNull
import org.jetbrains.kotlin.idea.base.scripting.projectStructure.ScriptModuleInfo
import org.jetbrains.kotlin.idea.base.util.isUnderKotlinSourceRootTypes
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.compilerAllowsAnyScriptsInSourceRoots
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.scriptingDebugLog
import org.jetbrains.kotlin.idea.hasNoExceptionsToBeUnderSourceRoot
import org.jetbrains.kotlin.idea.isEnabled
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import java.io.IOException
import java.io.OutputStream
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.isStandalone

class KotlinScriptSearchScope(project: Project, baseScope: GlobalSearchScope) : DelegatingGlobalSearchScope(project, baseScope) {
    override fun contains(file: VirtualFile): Boolean {
        return when (file) {
            KotlinScriptMarkerFileSystem.rootFile -> true
            else -> super.contains(file)
        }
    }
}

object KotlinScriptMarkerFileSystem : DummyFileSystem(), NonPhysicalFileSystem {
    override fun getProtocol() = "kotlin-script-dummy"

    val rootFile = object : VirtualFile() {
        override fun getFileSystem() = this@KotlinScriptMarkerFileSystem

        override fun getName() = "root"
        override fun getPath() = "/$name"

        override fun getLength(): Long = 0
        override fun isWritable() = false
        override fun isDirectory() = true
        override fun isValid() = true

        override fun getParent() = null
        override fun getChildren(): Array<VirtualFile> = emptyArray()

        override fun getTimeStamp(): Long = -1
        override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {}

        override fun contentsToByteArray() = throw IOException(IdeBundle.message("file.read.error", url))
        override fun getInputStream() = throw IOException(IdeBundle.message("file.read.error", url))

        override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream {
            throw IOException(IdeBundle.message("file.write.error", url))
        }
    }
}

class KotlinScriptResolveScopeProvider : ResolveScopeProvider() {

    override fun getResolveScope(file: VirtualFile, project: Project): GlobalSearchScope? {
        if (!file.isKotlinFileType()) return null

        val ktFile = PsiManager.getInstance(project).findFile(file) as? KtFile ?: return null
        val scriptDefinition = ktFile.findScriptDefinition() ?: return null

        // This is a workaround for completion in REPL to provide module dependencies
        if (scriptDefinition.baseClassType.fromClass == Any::class) return null

        val featureEnabled = LanguageFeature.SkipStandaloneScriptsInSourceRoots.isEnabled(ktFile.module, project)
        val backwardCompatibilityIsOn = compilerAllowsAnyScriptsInSourceRoots(project)

        ktFile.debugLog { "language-feature: ${featureEnabled}, backward-compatibility-flag: ${backwardCompatibilityIsOn}" }

        if (featureEnabled && !backwardCompatibilityIsOn) {
            return ktFile.getScopeAccordingToLanguageFeature(file, project, scriptDefinition)
        }

        if (!ktFile.isStandaloneScript()) {
            ktFile.debugLog { "=> in-module" }
            return null /* module scope, see method kdoc */
        }

        scriptingDebugLog(ktFile) { "resolving as standalone" }
        return ktFile.calculateScopeForStandaloneScript(file, project)
    }

    private fun KtFile.isStandaloneScript() = moduleInfoOrNull is ScriptModuleInfo // not ModuleSourceInfo (production|test)

    private fun KtFile.getScopeAccordingToLanguageFeature(
        file: VirtualFile,
        project: Project,
        scriptDefinition: ScriptDefinition
    ): GlobalSearchScope? {
        assert(LanguageFeature.SkipStandaloneScriptsInSourceRoots.isEnabled(module, project)) { "SkipStandaloneScriptsInSourceRoots is off" }

        return if (isStandaloneScriptByDesign(project, scriptDefinition)) {
            getScopeForStandaloneScript(file, project)
        } else {
            getScopeForNonStandaloneScript(file, project)
        }
    }

    private fun KtFile.getScopeForNonStandaloneScript(file: VirtualFile, project: Project): KotlinScriptSearchScope? {
        val underSourceRoot = isUnderKotlinSourceRootTypes()
        debugLog { "under-source-root: $underSourceRoot" }

        return if (underSourceRoot) {
            debugLog { "=> in-module" }
            null  // as designed
        } else {
            calculateScopeForStandaloneScript(file, project)  // the compiler doesn't forbid this, so do we
        }
    }

    private fun KtFile.getScopeForStandaloneScript(file: VirtualFile, project: Project): KotlinScriptSearchScope? {
        val underSourceRoot = isUnderKotlinSourceRootTypes()
        debugLog { "under-source-root: $underSourceRoot" }

        return if (underSourceRoot) {
            getScopeForStandaloneScriptUnderSourceRoot(file, project)
        } else {
            calculateScopeForStandaloneScript(file, project) // as designed
        }
    }

    private fun KtFile.getScopeForStandaloneScriptUnderSourceRoot(file: VirtualFile, project: Project): KotlinScriptSearchScope? {
        val hasNoExceptionToBeUnderSourceRoot = file.hasNoExceptionsToBeUnderSourceRoot()
        debugLog { "exception-to-be-under-source-root: ${!hasNoExceptionToBeUnderSourceRoot}" }

        return if (hasNoExceptionToBeUnderSourceRoot) {
            // We show the editor notification panel (file will be ignored at compilation), but allow resolution/highlighting work.
            calculateScopeForStandaloneScript(file, project)
        } else {
            debugLog { "=> in-module" }
            null // scripts not yet supporting "isStandalone" flag
        }
    }

    private fun KtFile.isStandaloneScriptByDesign(project: Project, definition: ScriptDefinition): Boolean {
        val configurationManager = ScriptConfigurationManager.getInstance(project)
        val configuration = configurationManager.getConfiguration(this)?.configuration
                            ?: definition.compilationConfiguration
        val isStandalone = configuration[ScriptCompilationConfiguration.isStandalone] == true
        debugLog { "standalone-by-design: $isStandalone" }
        return isStandalone
    }

    private fun KtFile.calculateScopeForStandaloneScript(file: VirtualFile, project: Project): KotlinScriptSearchScope {
        val vFile = virtualFile ?: viewProvider.virtualFile
        val dependenciesScope = ScriptConfigurationManager.getInstance(project).getScriptDependenciesClassFilesScope(vFile)
        debugLog { "=> standalone" }
        return KotlinScriptSearchScope(project, GlobalSearchScope.fileScope(project, file).uniteWith(dependenciesScope))
    }

    private fun KtFile.debugLog(message: () -> String) {
        scriptingDebugLog(this) { "[resolve-scope] ${message.invoke()}"}
    }
}