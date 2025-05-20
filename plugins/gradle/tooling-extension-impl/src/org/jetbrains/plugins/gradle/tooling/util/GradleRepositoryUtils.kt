// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleRepositoryUtils")

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.util

import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.IvyArtifactRepository


fun isIvyRepositoryUsed(project: Project): Boolean {
  return project.repositories
    .any { it is IvyArtifactRepository }
}
