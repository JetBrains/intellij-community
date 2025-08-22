// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.runtimeModuleRepository.generator

import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor

object RuntimeModuleRepositoryValidator {
  interface ErrorReporter {
    fun reportDuplicatingId(moduleId: String)
  }
  
  fun validate(descriptors: List<RawRuntimeModuleDescriptor>, errorReporter: ErrorReporter) {
    val moduleIDs = HashSet<String>()
    for (descriptor in descriptors) {
      if (!moduleIDs.add(descriptor.id)) {
        errorReporter.reportDuplicatingId(descriptor.id)
      }
    }
  }
}