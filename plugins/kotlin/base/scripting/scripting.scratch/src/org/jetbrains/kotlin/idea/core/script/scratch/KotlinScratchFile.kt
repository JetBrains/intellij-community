// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.scratch

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.SystemProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.base.psi.getTopmostElementAtOffset
import org.jetbrains.kotlin.idea.base.util.sdk
import org.jetbrains.kotlin.idea.core.script.k2.configurations.KotlinScriptService
import org.jetbrains.kotlin.idea.core.script.v1.ScratchFileOptions
import org.jetbrains.kotlin.idea.core.script.v1.ScratchFileOptionsByFile
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class KotlinScratchFile(val project: Project, val virtualFile: VirtualFile, val coroutineScope: CoroutineScope) {
    val executor: KotlinScratchExecutor = KotlinScratchExecutor(this, project, coroutineScope)

    val module: Module?
        get() = options.selectedModule?.let { ModuleManager.getInstance(project).findModuleByName(it) }

    val jdk: Sdk?
        get() {
            module?.let { return it.sdk }
            val jdkPath = options.selectedJdkHome ?: defaultScratchJavaHome
            return jdkPath?.let { findJavaByJavaHome(it) }
        }

    fun setModule(module: Module?) {
        ScratchFileOptionsByFile.update(project, virtualFile) {
            copy(selectedModule = module?.name)
        }

        reloadConfiguration()
    }

    fun selectJdk(jdk: Sdk) {
        saveOptions { copy(selectedJdkHome = jdk.homePath) }
        reloadConfiguration()
    }

    private fun reloadConfiguration() {
        coroutineScope.launch {
            KotlinScriptService.getInstance(project).reload(virtualFile)
        }
    }

    fun resetJdk() {
        saveOptions { copy(selectedJdkHome = null) }
    }

    fun getPsiFile(): PsiFile? = runReadAction {
        virtualFile.toPsiFile(project)
    }

    val options: ScratchFileOptions
        get() = ScratchFileOptionsByFile[project, virtualFile]

    fun saveOptions(update: ScratchFileOptions.() -> ScratchFileOptions) {
        ScratchFileOptionsByFile.update(project, virtualFile, update)
    }

    fun getExpressions(): List<ScratchExpression> = runReadAction {
        getPsiFile()?.let { getExpressions(it) } ?: emptyList()
    }

    fun getExpressionAtLine(line: Int): ScratchExpression? = getExpressions().find { line in it.lineStart..it.lineEnd }

    private fun getExpressions(psiFile: PsiFile): List<ScratchExpression> {
        // todo multiple expressions at one line
        val doc = PsiDocumentManager.getInstance(psiFile.project).getLastCommittedDocument(psiFile) ?: return emptyList()
        var line = 0
        val result = arrayListOf<ScratchExpression>()
        while (line < doc.lineCount) {
            var start = doc.getLineStartOffset(line)
            var element = psiFile.findElementAt(start)
            if (element is PsiWhiteSpace || element is PsiComment) {
                start = PsiTreeUtil.skipSiblingsForward(
                    element,
                    PsiWhiteSpace::class.java,
                    PsiComment::class.java
                )?.startOffset ?: start
                element = psiFile.findElementAt(start)
            }

            element = element?.let {
                getTopmostElementAtOffset(
                    it,
                    start,
                    KtImportDirective::class.java,
                    KtDeclaration::class.java
                )
            }

            if (element == null) {
                line++
                continue
            }

            val scratchExpression = ScratchExpression(
                element,
                doc.getLineNumber(element.startOffset),
                doc.getLineNumber(element.endOffset)
            )
            result.add(scratchExpression)

            line = scratchExpression.lineEnd + 1
        }

        return result
    }
}

data class ScratchExpression(val element: PsiElement, val lineStart: Int, val lineEnd: Int = lineStart)

private const val KOTLIN_SCRATCH_JDK_NAME: String = "Kotlin Scratch JDK"

private fun findJavaByJavaHome(jdkHome: String): Sdk? {
    val javaSdk = JavaSdk.getInstance()
    return ProjectJdkTable.getInstance().allJdks.firstOrNull { sdk ->
        sdk.sdkType is JavaSdkType && FileUtil.pathsEqual(sdk.homePath, jdkHome)
    } ?: javaSdk.takeIf { it.isValidSdkHome(jdkHome) }
        ?.createJdk(javaSdk.suggestSdkName(null, jdkHome).ifBlank { KOTLIN_SCRATCH_JDK_NAME }, jdkHome, false)
}

val defaultScratchJavaHome: String?
    get() = sequenceOf(System.getenv("JAVA_HOME"), SystemProperties.getJavaHome())
        .filterNotNull()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .firstOrNull { JavaSdk.getInstance().isValidSdkHome(it) }

fun scratchModuleSdkHome(project: Project, virtualFile: VirtualFile): String? {
    val moduleName = ScratchFileOptionsByFile[project, virtualFile].selectedModule ?: return null
    val module = ModuleManager.getInstance(project).findModuleByName(moduleName) ?: return null
    val sdk = ModuleRootManager.getInstance(module).sdk ?: return null
    return sdk.takeIf { it.sdkType is JavaSdkType }?.homePath
}
