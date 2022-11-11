// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.module

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleGrouper
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry

fun getQualifiedNameModuleGrouper(project: Project): ModuleGrouper {
  return runWithQualifiedModuleNamesEnabled { ModuleGrouper.instanceFor(project) }
}

fun <T> runWithQualifiedModuleNamesEnabled(action: () -> T): T {
  val property = Registry.get("project.qualified.module.names")
  val oldValue = property.asBoolean()
  return try {
    property.setValue(true)
    action()
  }
  finally {
    property.setValue(oldValue)
  }
}

fun renameModule(module: Module, newName: String) {
  val model = ModuleManager.getInstance(module.project).getModifiableModel()
  model.renameModule(module, newName)
  runWriteAction { model.commit() }
}