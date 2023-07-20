// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.build

import com.intellij.compiler.impl.BuildTargetScopeProvider
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.compiler.CompileScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.idea.devkit.util.PsiUtil
import org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope

class IntelliJModuleRepositoryBuildScopeProvider : BuildTargetScopeProvider() {
  override fun getBuildTargetScopes(baseScope: CompileScope,
                                    project: Project,
                                    forceBuild: Boolean): List<TargetTypeBuildScope> {
    if (Registry.`is`("devkit.generate.intellij.module.repository") && runReadAction { PsiUtil.isIdeaProject(project) }) {
      val scope = TargetTypeBuildScope.newBuilder()
        .setTypeId(TARGET_TYPE_ID)
        .setAllTargets(true)
        .setForceBuild(forceBuild)
        .build()
      return listOf(scope)
      
    }
    return emptyList()
  }
  
  companion object {
    /**
     * Must be equal to [com.intellij.devkit.runtimeModuleRepository.jps.build.RuntimeModuleRepositoryBuildConstants.TARGET_TYPE_ID].
     */
    private const val TARGET_TYPE_ID = "intellij-runtime-module-repository"
  }
}