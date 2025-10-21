// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.k2.dependencies

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.defaultContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.ResolveScopeProvider
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.util.isUnderKotlinSourceRootTypes
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.core.script.v1.*
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptConfigurationsProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.isStandalone

class KotlinScriptResolveScopeProvider : ResolveScopeProvider() {

    override fun getResolveScope(file: VirtualFile, project: Project): GlobalSearchScope? =
        getResolveScope(file, defaultContext(), project)

    override fun getResolveScope(file: VirtualFile, context: CodeInsightContext, project: Project): GlobalSearchScope? {
        if (!file.isKotlinFileType()) return null

        val ktFile = PsiManager.getInstance(project).findFile(file, context) as? KtFile ?: return null
        val scriptDefinition = ktFile.findScriptDefinition() ?: return null

        // This is a workaround for completion in REPL to provide module dependencies
        if (scriptDefinition.baseClassType.fromClass == Any::class) return null

        val featureEnabled = LanguageFeature.SkipStandaloneScriptsInSourceRoots.isEnabled(ktFile.module, project)
        val backwardCompatibilityIsOn = compilerAllowsAnyScriptsInSourceRoots(project)

        ktFile.debugLog { "language-feature: ${featureEnabled}, backward-compatibility-flag: ${backwardCompatibilityIsOn}" }

        if (featureEnabled && !backwardCompatibilityIsOn) {
            return ktFile.getScopeAccordingToLanguageFeature(file, project, scriptDefinition)
        }

        scriptingDebugLog(ktFile) { "resolving as standalone" }
        return ktFile.calculateScopeForStandaloneScript(file, project)
    }

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
        val configuration = ScriptConfigurationsProvider.getInstance(project)?.getScriptConfiguration(this)?.configuration
            ?: definition.compilationConfiguration
        val isStandalone = configuration[ScriptCompilationConfiguration.isStandalone] == true
        debugLog { "standalone-by-design: $isStandalone" }
        return isStandalone
    }

    private fun KtFile.calculateScopeForStandaloneScript(file: VirtualFile, project: Project): KotlinScriptSearchScope {
        val vFile = virtualFile ?: viewProvider.virtualFile
        val dependenciesScope =
            ScriptDependencyAware.getInstance(project).getScriptDependenciesClassFilesScope(vFile)
        debugLog { "=> standalone" }
        return KotlinScriptSearchScope(project, GlobalSearchScope.fileScope(project, file).uniteWith(dependenciesScope))
    }

    private fun KtFile.debugLog(message: () -> String) {
        scriptingDebugLog(this) { "[resolve-scope] ${message.invoke()}" }
    }
}