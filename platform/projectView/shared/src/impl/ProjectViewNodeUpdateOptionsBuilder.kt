// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.impl

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ProjectViewNodeUpdateOptionsBuilder {
  var deep: Boolean
}

internal interface ProjectViewNodeUpdateOptions {
  val deep: Boolean
  fun merge(other: ProjectViewNodeUpdateOptions): ProjectViewNodeUpdateOptions
}

internal data class ProjectViewNodeUpdateOptionsBuilderImpl(
  override var deep: Boolean = false,
) : ProjectViewNodeUpdateOptionsBuilder, ProjectViewNodeUpdateOptions {
  override fun merge(other: ProjectViewNodeUpdateOptions): ProjectViewNodeUpdateOptions {
    return ProjectViewNodeUpdateOptionsBuilderImpl(
      deep = this.deep || other.deep,
    )
  }
}
