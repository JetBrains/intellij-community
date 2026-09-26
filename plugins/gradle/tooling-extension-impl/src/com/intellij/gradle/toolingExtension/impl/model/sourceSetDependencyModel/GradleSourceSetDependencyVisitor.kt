// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetDependencyModel

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.tasks.SourceSetOutput
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext

interface GradleSourceSetDependencyVisitor {

  fun visitConfiguration(configuration: Configuration)

  fun visitSourceSetOutput(sourceSetOutput: SourceSetOutput)

  fun visitFileCollection(fileCollection: FileCollection)

  fun visitFile(file: FileSystemLocation)

  companion object {
    @JvmStatic
    fun traverse(context: ModelBuilderContext, project: Project, source: Any, visitor: GradleSourceSetDependencyVisitor) {
      GradleSourceSetDependencyTraverser(context, project).traverse(source, visitor)
    }
  }
}