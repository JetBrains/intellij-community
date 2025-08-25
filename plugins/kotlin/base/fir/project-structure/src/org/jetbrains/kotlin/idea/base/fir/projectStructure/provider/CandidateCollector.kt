// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.provider

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.storage.EntityPointer
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.psi.PsiFile
import com.intellij.util.SmartList
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileSetRecognizer
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.impl.base.projectStructure.KaBuiltinsModuleImpl
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltinsVirtualFileProvider
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.fir.projectStructure.FirKaModuleFactory
import org.jetbrains.kotlin.idea.base.util.getOutsiderFileOrigin
import org.jetbrains.kotlin.idea.base.util.isOutsiderFile
import org.jetbrains.kotlin.psi.KtFile

internal object CandidateCollector {
    fun collectCandidates(
      psiFile: PsiFile?,
      virtualFile: VirtualFile?,
      project: Project,
    ): Sequence<ModuleCandidate> {
        return sequence {
            yieldAll(collectCandidatesByExtensions(psiFile))
            yieldAll(collectCandidatesByVirtualFile(virtualFile, project))
            yieldAll(collectBuiltinModuleCandidates(virtualFile, psiFile, project))
        }
    }

    private fun collectCandidatesByExtensions(psiFile: PsiFile?): Collection<ModuleCandidate> {
        if (psiFile == null) return emptyList()
        val data = SmartList<ModuleCandidate>()
        for (factory in FirKaModuleFactory.EP_NAME.extensionList) {
            val kaModule = factory.createKaModuleByPsiFile(psiFile) ?: continue
            data += ModuleCandidate.FixedModule(kaModule)
        }
        return data
    }

    @OptIn(KaImplementationDetail::class)
    private fun collectBuiltinModuleCandidates(
      virtualFile: VirtualFile?,
      psiFile: PsiFile?,
      project: Project
    ): Collection<ModuleCandidate> {
        if (virtualFile == null || psiFile !is KtFile) return emptyList()
        if (virtualFile in BuiltinsVirtualFileProvider.getInstance().getBuiltinVirtualFiles()) {
            return listOf(ModuleCandidate.FixedModule(KaBuiltinsModuleImpl(psiFile.platform, project)))
        }
        return emptyList()
    }

    fun collectCandidatesByVirtualFile(virtualFile: VirtualFile?, project: Project): Sequence<ModuleCandidate> {
        if (virtualFile == null) return emptySequence()
        val originalVirtualFileForOutsider = if (isOutsiderFile(virtualFile)) getOutsiderFileOrigin(project, virtualFile) else null

        val workspaceModel = WorkspaceModel.getInstance(project)
        val workspaceFileIndex = WorkspaceFileIndexEx.getInstance(project)
        val fileSets = workspaceFileIndex.getFileInfo(
          originalVirtualFileForOutsider ?: virtualFile,
          honorExclusion = false,
          includeContentSets = true,
          includeContentNonIndexableSets = true,
          includeExternalSets = true,
          includeExternalSourceSets = true,
          includeCustomKindSets = true
        ).fileSets

        return fileSets
            .asSequence()
            .mapNotNull { fileSet ->
                fileSetToCandidate(
                    fileSet = fileSet,
                    virtualFile = virtualFile,
                    originalVirtualFileForOutsider = originalVirtualFileForOutsider,
                    workspaceModel = workspaceModel
                )
            }
    }

    private fun fileSetToCandidate(
      fileSet: WorkspaceFileSetWithCustomData<*>,
      virtualFile: VirtualFile,
      originalVirtualFileForOutsider: VirtualFile?,
      workspaceModel: WorkspaceModel
    ): ModuleCandidate? {
        val storage = workspaceModel.currentSnapshot
        val entityPointer: EntityPointer<*> = WorkspaceFileSetRecognizer.getEntityPointer(fileSet) ?: return null
        val entity: WorkspaceEntity = entityPointer.resolve(storage) ?: return null

        return when {
            originalVirtualFileForOutsider == null -> ModuleCandidate.Entity(entity, fileSet.kind)
            entity is SourceRootEntity -> ModuleCandidate.OutsiderFileEntity(entity, virtualFile, originalVirtualFileForOutsider)
            else -> null
        }
    }
}