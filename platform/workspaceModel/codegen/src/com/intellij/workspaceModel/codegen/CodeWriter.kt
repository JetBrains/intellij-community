// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import deft.storage.codegen.javaImplName
import org.jetbrains.deft.codegen.ijws.implWsCode
import org.jetbrains.deft.codegen.ijws.wsModuleCode
import org.jetbrains.deft.codegen.model.DefType
import org.jetbrains.deft.codegen.model.KtObjModule
import org.jetbrains.deft.codegen.patcher.rewrite
import org.jetbrains.deft.codegen.utils.fileContents
import org.jetbrains.deft.impl.ObjModule
import java.io.File

fun DefType.implIjWsFileContents(simpleTypes: List<DefType>): String {
  return fileContents(def.file!!.pkg.fqn, """
            ${implWsCode(simpleTypes)}
        """.trim(), def.file?.imports?.list)
}

object CodeWriter {
  fun generate(project: Project, sourceFolder: VirtualFile, targetFolder: VirtualFile, moduleId: String) {
    val documentManager = FileDocumentManager.getInstance()
    val ktSrcs = sourceFolder.children.filter { it.extension == "kt" }.mapNotNull {
      val document = documentManager.getDocument(it) ?: return@mapNotNull null
      it to document
    }

    val module = KtObjModule(project, ObjModule.Id(moduleId))
    ktSrcs.forEach { (vfu, document) ->
      module.addFile(vfu.name, vfu) { document.text }
    }
    val result = module.build()
    module.files.forEach {
      val virtualFile = sourceFolder.findChild(it.name) ?: return@forEach
      documentManager.getDocument(virtualFile)?.setText(it.rewrite())
    }

    result.typeDefs.filterNot { it.name == "WorkspaceEntity" || it.name == "WorkspaceEntityWithPersistentId" || it.abstract }.forEach {
      val virtualFile = targetFolder.createChildData(this, it.javaImplName + ".kt")
      documentManager.getDocument(virtualFile)?.setText(it.implIjWsFileContents(result.simpleTypes))
    }
    val virtualFile = targetFolder.parent.createChildData(this, module.moduleObjName + ".kt")
    documentManager.getDocument(virtualFile)?.setText(result.wsModuleCode())
  }

  fun generate(dir: File, fromDirectory: String, toDirectory: String, moduleId: String) {
    val generatedDestDir = dir.resolve(toDirectory)
    generatedDestDir.mkdirs()
    val ktSrcs = dir.resolve(fromDirectory).listFiles()!!
      .toList()
      .filter { it.name.endsWith(".kt") }

    val module = KtObjModule(null, ObjModule.Id(moduleId))
    ktSrcs.forEach {
      module.addFile(it.relativeTo(dir).path, null) { it.readText() }
    }
    val result = module.build()
    module.files.forEach {
      dir.resolve(it.name).writeText(it.rewrite())
    }
    result.typeDefs.filterNot { it.name == "WorkspaceEntity" || it.name == "WorkspaceEntityWithPersistentId" || it.abstract }.forEach {
      generatedDestDir
        .resolve(it.javaImplName + ".kt")
        .writeText(it.implIjWsFileContents(result.simpleTypes))
    }
    dir.resolve(module.moduleObjName + ".kt").writeText(result.wsModuleCode())
  }
}