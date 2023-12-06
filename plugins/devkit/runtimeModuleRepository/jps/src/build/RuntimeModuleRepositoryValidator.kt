// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.runtimeModuleRepository.jps.build

import com.intellij.devkit.runtimeModuleRepository.jps.impl.DevkitRuntimeModuleRepositoryJpsBundle
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import org.jetbrains.annotations.Nls

object RuntimeModuleRepositoryValidator {
  fun validate(descriptors: List<RawRuntimeModuleDescriptor>, errorReporter: (@Nls String) -> Unit) {
    val moduleIDs = HashSet<String>()
    for (descriptor in descriptors) {
      if (!moduleIDs.add(descriptor.id)) {
        errorReporter(DevkitRuntimeModuleRepositoryJpsBundle.message("error.message.duplicating.id.0.is.found", descriptor.id))
      }
    }
  }
}