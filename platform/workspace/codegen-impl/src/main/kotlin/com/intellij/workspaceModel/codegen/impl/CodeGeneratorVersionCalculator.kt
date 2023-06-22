// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl

import com.intellij.workspaceModel.codegen.impl.engine.CodeGeneratorImpl

object CodeGeneratorVersionCalculator {
  val implementationMajorVersion: String
    get() = CodeGeneratorImpl::class.java.`package`.implementationVersion

  val apiVersion: String
    get() = CodeGeneratorImpl::class.java.`package`.specificationVersion
}