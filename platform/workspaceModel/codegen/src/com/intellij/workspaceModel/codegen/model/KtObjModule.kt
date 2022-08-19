// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.deft.model

import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.codegen.deft.ObjModule

class KtObjModule(
  val project: Project?,
  val keepUnknownFields: Boolean = false,
) : ObjModule() {
  val packages = mutableMapOf<String?, KtPackage>()
  fun getOrCreatePackage(p: String?): KtPackage = packages.getOrPut(p) { KtPackage(p) }

  init {
    packages.values.forEach { packageToImport ->
      getOrCreatePackage(packageToImport.fqn)
        .scope.importedScopes.add(packageToImport.scope)
    }
  }

  private var nextTypeId = 1
  fun nextTypeId() = nextTypeId++

}