// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer

import com.intellij.workspaceModel.codegen.deft.meta.CompiledObjModule
import com.intellij.workspaceModel.codegen.engine.GenerationProblem
import com.intellij.workspaceModel.codegen.engine.ProblemLocation
import com.intellij.workspaceModel.codegen.impl.engine.ProblemReporter
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isComputable
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isRefType

internal fun checkExtensionFields(module: CompiledObjModule, reporter: ProblemReporter) {
  module.extensions.forEach { extProperty ->
    if (!extProperty.valueType.isRefType() && !extProperty.isComputable) {
      reporter.reportProblem(GenerationProblem("Extension property is supposed to be a reference to another entity only.",
                                               GenerationProblem.Level.ERROR, ProblemLocation.Property(extProperty)))
    }
  }
}
