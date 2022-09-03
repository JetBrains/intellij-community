// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.deft.model

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.workspaceModel.codegen.deft.ObjModule
import com.intellij.workspaceModel.codegen.deft.ExtField
import com.intellij.workspaceModel.codegen.patcher.KotlinReader
import com.intellij.workspaceModel.codegen.patcher.PsiKotlinReader

class KtObjModule(
  val project: Project?,
  val keepUnknownFields: Boolean = false,
) : ObjModule() {
  val packages = mutableMapOf<String?, KtPackage>()
  val files = mutableListOf<KtFile>()
  fun getOrCreatePackage(p: String?): KtPackage = packages.getOrPut(p) { KtPackage(p) }

  init {
    packages.values.forEach { packageToImport ->
      getOrCreatePackage(packageToImport.fqn)
        .scope.importedScopes.add(packageToImport.scope)
    }
  }

  fun addFile(name: String, virtualFile: VirtualFile?, content: () -> String): KtFile {
    val file = KtFile(this, content, name, virtualFile)
    val reader = KotlinReader(file)
    reader.read()
    files.add(file)
    return file
  }

  fun addPsiFile(name: String, virtualFile: VirtualFile?, content: () -> String): KtFile {
    val file = KtFile(this, content, name, virtualFile)
    val reader = PsiKotlinReader(file)
    reader.read()
    files.add(file)
    return file
  }

  fun build(diagnostics: Diagnostics = Diagnostics()): Built {
    val simpleTypes = mutableListOf<DefType>()
    files.forEach {
      it.scope.visitSimpleTypes(simpleTypes)
    }
    simpleTypes.forEach { it.def.buildFields(diagnostics) }
    simpleTypes.forEach { it.verify(diagnostics) }

    val types = mutableListOf<DefType>()
    files.forEach {
      it.scope.visitTypes(types)
    }

    types.forEach { it.def.buildFields(diagnostics) }
    types.forEach { it.verify(diagnostics) }
    if (types.isEmpty()) {
      beginInit(0)
    }
    else {
      beginInit(types.maxOf { it.id })
      types.forEach { add(it) }
    }

    files.forEach { f ->
      f.block.defs.forEach {
        it.toExtField(f.scope, this, diagnostics)
      }
    }

    require()
    return Built(this, types, simpleTypes, extFields)
  }

  private var nextTypeId = 1
  fun nextTypeId() = nextTypeId++

  class Built(
    val src: KtObjModule,
    val typeDefs: List<DefType>,
    val simpleTypes: List<DefType>,
    val extFields: MutableList<ExtField<*, *>>
  )
}