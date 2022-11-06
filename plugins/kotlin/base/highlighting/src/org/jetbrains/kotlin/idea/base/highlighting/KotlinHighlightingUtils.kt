// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("KotlinHighlightingUtils")

package org.jetbrains.kotlin.idea.base.highlighting

import com.intellij.codeInsight.daemon.OutsidersPsiFileSupport
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils
import org.jetbrains.kotlin.idea.base.util.getOutsiderFileOrigin
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.NotUnderContentRootModuleInfo
import org.jetbrains.kotlin.idea.core.script.IdeScriptReportSink
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager
import com.intellij.openapi.application.runReadAction
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtFile
import kotlin.script.experimental.api.ScriptDiagnostic

@ApiStatus.Internal
fun KtFile.shouldHighlightErrors(): Boolean {
    if (isCompiled) {
        return false
    }

    if (this is KtCodeFragment && context != null) {
        return true
    }

    val canCheckScript = shouldCheckScript()
    if (canCheckScript == true) {
        return this.shouldHighlightScript()
    }

    return RootKindFilter.projectSources.copy(includeScriptsOutsideSourceRoots = canCheckScript == null).matches(this)
}

@ApiStatus.Internal
fun KtFile.shouldHighlightFile(): Boolean {
    if (this is KtCodeFragment && context != null) {
        return true
    }

    if (isCompiled) return false

    if (OutsidersPsiFileSupport.isOutsiderFile(virtualFile)) {
        val origin = getOutsiderFileOrigin(project, virtualFile) ?: return false
        val psiFileOrigin = PsiManager.getInstance(project).findFile(origin) as? KtFile ?: return false
        return psiFileOrigin.shouldHighlightFile()
    }

    val shouldCheckScript = shouldCheckScript()
    if (shouldCheckScript == true) {
        return this.shouldHighlightScript()
    }

    return if (shouldCheckScript != null) {
        RootKindFilter.everything.matches(this) && moduleInfo !is NotUnderContentRootModuleInfo
    } else {
        RootKindFilter.everything.copy(includeScriptsOutsideSourceRoots = true).matches(this)
    }
}

private fun KtFile.shouldCheckScript(): Boolean? = runReadAction {
    when {
        // to avoid SNRE from stub (KTIJ-7633)
        DumbService.getInstance(project).isDumb -> null
        isScript() -> true
        else -> false
    }
}

private fun KtFile.shouldHighlightScript(): Boolean {
    if (KotlinPlatformUtils.isCidr) {
        // There is no Java support in CIDR. So do not highlight errors in KTS if running in CIDR.
        return false
    }

    if (!ScriptConfigurationManager.getInstance(project).hasConfiguration(this)) return false
    if (IdeScriptReportSink.getReports(this).any { it.severity == ScriptDiagnostic.Severity.FATAL }) {
        return false
    }

    if (!ScriptDefinitionsManager.getInstance(project).isReady()) return false

    return RootKindFilter.projectSources.copy(includeScriptsOutsideSourceRoots = true).matches(this)
}