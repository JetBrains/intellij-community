// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.base.fir.scripting.projectStructure

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.base.fir.scripting.projectStructure.modules.KaScriptDependencyLibraryModuleImpl
import org.jetbrains.kotlin.base.fir.scripting.projectStructure.modules.KaScriptModuleImpl
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.fir.projectStructure.K2KaModuleFactory
import org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtFile


internal class K2ScriptingKaModuleFactory : K2KaModuleFactory {
    override fun createKaModuleByPsiFile(file: PsiFile): KaModule? {
        val ktFile = file as? KtFile ?: return null

        if (ktFile.kotlinParserWillCreateKtScriptHere()) {
            val project = file.project
            val virtualFile = file.originalFile.virtualFile
            return KaScriptModuleImpl(project, virtualFile)
        }

        return null
    }

    /**
     * From https://github.com/JetBrains/kotlin/blob/b4b1c7cd698c1e8276a0bed504f22b93582d4f2e/compiler/psi/src/org/jetbrains/kotlin/parsing/KotlinParser.java#L46
     *
     * to avoid accessing stubs inside
     */
    private fun KtFile.kotlinParserWillCreateKtScriptHere(): Boolean {
        if (this is KtCodeFragment) return false
        val extension = FileUtilRt.getExtension(name)
        val isRegularKtFile = extension.isEmpty() || extension == KotlinFileType.EXTENSION || isCompiled
        return !isRegularKtFile
    }

    override fun createSpecialLibraryModule(libraryEntity: LibraryEntity, project: Project): KaLibraryModule? {
        if (libraryEntity.entitySource is KotlinScriptEntitySource) {
            return KaScriptDependencyLibraryModuleImpl(libraryEntity.symbolicId, project)
        }
        return null
    }
}