// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction.impl.extensions

import com.intellij.openapi.externalSystem.util.Order
import com.intellij.platform.externalSystem.impl.dependencySubstitution.DependencySubstitutionUtil
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncExtension
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase

@ApiStatus.Internal
@Order(GradleDependencySubstitutionSyncExtension.ORDER)
class GradleDependencySubstitutionSyncExtension : GradleSyncExtension {

  override fun updateProjectModel(
    context: ProjectResolverContext,
    syncStorage: MutableEntityStorage,
    projectStorage: MutableEntityStorage,
    phase: GradleSyncPhase,
  ) {
    DependencySubstitutionUtil.updateDependencySubstitutions(projectStorage)
  }

  companion object {

    const val ORDER: Int = GradleBaseSyncExtension.ORDER + 10000
  }
}