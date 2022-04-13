package com.intellij.workspace.model

import com.intellij.openapi.fileEditor.FileDocumentManager
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

fun generate(sourceFolder: VirtualFile, targetFolder: VirtualFile) {
  CodeWriter().generate(sourceFolder, targetFolder, "org.jetbrains.workspaceModel")
}

fun DefType.implIjWsFileContents(simpleTypes: List<DefType>): String {
  return fileContents(def.file!!.pkg.fqn, """
            ${implWsCode(simpleTypes)}
        """.trim())
}

open class CodeWriter() {
  open fun File.writeCode(code: String) {
    writeCodeInternal(this, code)
  }

  private fun writeCodeInternal(file: File, code: String) {
    file.writeText(code)
  }

  fun generate(sourceFolder: VirtualFile, targetFolder: VirtualFile, moduleId: String) {
    val documentManager = FileDocumentManager.getInstance()
    val generatedDestDir = targetFolder.toNioPath().toFile()
    val ktSrcs = sourceFolder.children.filter { it.extension == "kt" }.mapNotNull {
      val document = documentManager.getDocument(it) ?: return@mapNotNull null
      it to document
    }

    val module = KtObjModule(ObjModule.Id(moduleId))
    ktSrcs.forEach { (vfu, document) ->
      module.addFile(vfu.name) { document.text }
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
    //        dir.resolve("toIjWs/generated.kt").writeCode(result.ijWsCode())
  }
}