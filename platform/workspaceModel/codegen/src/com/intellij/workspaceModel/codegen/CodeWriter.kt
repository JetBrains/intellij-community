// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.workspaceModel.codegen.deft.model.DefType
import com.intellij.workspaceModel.codegen.deft.model.KtObjModule
import com.intellij.workspaceModel.codegen.patcher.rewrite
import com.intellij.workspaceModel.codegen.utils.fileContents
import com.intellij.workspaceModel.storage.*
import java.io.File

val SKIPPED_TYPES = setOf(WorkspaceEntity::class.simpleName,
                          ReferableWorkspaceEntity::class.simpleName,
                          ModifiableWorkspaceEntity::class.simpleName,
                          ModifiableReferableWorkspaceEntity::class.simpleName,
                          WorkspaceEntityWithPersistentId::class.simpleName)

fun DefType.implIjWsFileContents(simpleTypes: List<DefType>): String {
  return fileContents(def.file!!.pkg.fqn, """
            ${implWsCode(simpleTypes)}
        """.trim(), def.file?.imports?.list)
}

private val LOG = logger<CodeWriter>()

object CodeWriter {
  @RequiresWriteLock
  fun generate(project: Project, sourceFolder: VirtualFile,  keepUnknownFields: Boolean, targetFolderGenerator: () -> VirtualFile?) {
    val documentManager = FileDocumentManager.getInstance()
    val ktSrcs = mutableListOf<Pair<VirtualFile, Document>>()
    val fileMapping = mutableMapOf<String, VirtualFile>()
    VfsUtilCore.processFilesRecursively(sourceFolder) {
      if (it.extension == "kt") {
        val document = documentManager.getDocument(it) ?: return@processFilesRecursively true
        ktSrcs.add(it to document)
        fileMapping[it.name] = it
      }
      return@processFilesRecursively true
    }

    val module = KtObjModule(project, keepUnknownFields = keepUnknownFields)
    ktSrcs.forEach { (vfu, document) ->
      module.addPsiFile(vfu.name, vfu) { document.text }
    }
    val result = module.build()
    val entitiesForGeneration = result.typeDefs.filterNot {  it.utilityType || it.abstract }
    if (!entitiesForGeneration.isEmpty()) {
      val genFolder = targetFolderGenerator.invoke()
      if (genFolder == null) {
        LOG.info("Generated source folder doesn't exist. Skip processing source folder with path: ${sourceFolder}")
        return
      }
      module.files.forEach {
        val virtualFile = fileMapping[it.name] ?: return@forEach
        val fileContent = it.rewrite() ?: return@forEach
        documentManager.getDocument(virtualFile)?.setText(fileContent)
      }

      entitiesForGeneration.forEach {
        val sourceFile = it.def.file?.virtualFile ?: error("Source file for ${it.def.name} doesn't exist")
        val packageFolder = createPackageFolderIfMissing(sourceFolder, sourceFile, genFolder)
        val virtualFile = packageFolder.createChildData(this, it.javaImplName + ".kt")
        documentManager.getDocument(virtualFile)?.setText(it.implIjWsFileContents(result.simpleTypes))
      }
    } else {
      LOG.info("Not found types for generation")
    }
  }

  fun generate(dir: File, fromDirectory: String, generatedDestDir: File, keepUnknownFields: Boolean) {
    generatedDestDir.mkdirs()
    val ktSrcs = dir.resolve(fromDirectory).listFiles()!!
      .toList()
      .filter { it.name.endsWith(".kt") }

    val module = KtObjModule(null, keepUnknownFields)
    ktSrcs.forEach {
      module.addFile(it.relativeTo(dir).path, null) { it.readText() }
    }
    val result = module.build()
    module.files.forEach {
      val fileContent = it.rewrite() ?: return@forEach
      dir.resolve(it.name).writeText(fileContent)
    }
    result.typeDefs.filterNot { it.name == "WorkspaceEntity" || it.name == "WorkspaceEntityWithPersistentId" || it.abstract }.forEach {
      generatedDestDir
        .resolve(it.javaImplName + ".kt")
        .writeText(it.implIjWsFileContents(result.simpleTypes))
    }
  }

  private fun createPackageFolderIfMissing(sourceRoot: VirtualFile, sourceFile: VirtualFile, genFolder: VirtualFile): VirtualFile {
    val relativePath = VfsUtil.getRelativePath(sourceFile.parent, sourceRoot, '/')
    return VfsUtil.createDirectoryIfMissing(genFolder, "$relativePath")
  }
}