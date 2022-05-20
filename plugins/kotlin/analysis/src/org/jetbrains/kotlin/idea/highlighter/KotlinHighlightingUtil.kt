// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.OutsidersPsiFileSupport
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.base.util.KotlinPlatformUtils
import org.jetbrains.kotlin.base.util.getOutsiderFileOrigin
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.NotUnderContentRootModuleInfo
import org.jetbrains.kotlin.idea.caches.resolve.util.isInDumbMode
import org.jetbrains.kotlin.idea.core.script.IdeScriptReportSink
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtFile
import kotlin.script.experimental.api.ScriptDiagnostic

object KotlinHighlightingUtil {
    fun shouldHighlight(psiElement: PsiElement): Boolean {
        val ktFile = psiElement.containingFile as? KtFile ?: return false
        return shouldHighlightFile(ktFile)
    }

    private fun shouldHighlightFile(ktFile: KtFile): Boolean {
        if (ktFile is KtCodeFragment && ktFile.context != null) {
            return true
        }

        if (ktFile.isCompiled) return false

        if (OutsidersPsiFileSupport.isOutsiderFile(ktFile.virtualFile)) {
            val origin = getOutsiderFileOrigin(ktFile.project, ktFile.virtualFile) ?: return false
            val psiFileOrigin = PsiManager.getInstance(ktFile.project).findFile(origin) ?: return false
            return shouldHighlight(psiFileOrigin)
        }

        val shouldCheckScript = ktFile.shouldCheckScript()
        if (shouldCheckScript == true) {
            return shouldHighlightScript(ktFile)
        }

        return if (shouldCheckScript != null) {
            RootKindFilter.everything.matches(ktFile) && ktFile.moduleInfo !is NotUnderContentRootModuleInfo
        } else {
            RootKindFilter.everything.copy(includeScriptsOutsideSourceRoots = true).matches(ktFile)
        }
    }

    fun shouldHighlightErrors(ktFile: KtFile): Boolean {
        if (ktFile.isCompiled) {
            return false
        }

        if (ktFile is KtCodeFragment && ktFile.context != null) {
            return true
        }

        val canCheckScript = ktFile.shouldCheckScript()
        if (canCheckScript == true) {
            return shouldHighlightScript(ktFile)
        }

        return RootKindFilter.projectSources.copy(includeScriptsOutsideSourceRoots = canCheckScript == null).matches(ktFile)
    }

    private fun KtFile.shouldCheckScript(): Boolean? = runReadAction {
        when {
            // to avoid SNRE from stub (KTIJ-7633)
            project.isInDumbMode() -> null
            isScript() -> true
            else -> false
        }
    }

    private fun shouldHighlightScript(ktFile: KtFile): Boolean {
        if (KotlinPlatformUtils.isCidr) {
            // There is no Java support in CIDR. So do not highlight errors in KTS if running in CIDR.
            return false
        }

        if (!ScriptConfigurationManager.getInstance(ktFile.project).hasConfiguration(ktFile)) return false
        if (IdeScriptReportSink.getReports(ktFile).any { it.severity == ScriptDiagnostic.Severity.FATAL }) {
            return false
        }

        if (!ScriptDefinitionsManager.getInstance(ktFile.project).isReady()) return false

        return RootKindFilter.projectSources.copy(includeScriptsOutsideSourceRoots = true).matches(ktFile)
    }

    fun hasCustomPropertyDeclaration(descriptor: PropertyDescriptor): Boolean {
        var hasCustomPropertyDeclaration = false
        if (!hasExtensionReceiverParameter(descriptor)) {
            if (descriptor.getter?.isDefault == false || descriptor.setter?.isDefault == false)
                hasCustomPropertyDeclaration = true
        }
        return hasCustomPropertyDeclaration
    }

    fun hasExtensionReceiverParameter(descriptor: PropertyDescriptor): Boolean {
        return descriptor.extensionReceiverParameter != null
    }
}