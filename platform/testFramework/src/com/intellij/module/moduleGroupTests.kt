/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.module

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleGrouper
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry

/**
 * @author nik
 */
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
  val model = ModuleManager.getInstance(module.project).modifiableModel
  model.renameModule(module, newName)
  runWriteAction { model.commit() }
}