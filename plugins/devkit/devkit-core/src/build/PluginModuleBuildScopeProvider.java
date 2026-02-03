// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.build;

import com.intellij.compiler.impl.BuildTargetScopeProvider;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.jps.api.CmdlineProtoUtil;
import org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTargetType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class PluginModuleBuildScopeProvider extends BuildTargetScopeProvider {
  @Override
  public @NotNull List<TargetTypeBuildScope> getBuildTargetScopes(@NotNull CompileScope baseScope, @NotNull Project project, boolean forceBuild) {
    List<String> pluginArtifactTargetIds = new ArrayList<>();
    for (Module module : baseScope.getAffectedModules()) {
      if (PluginModuleType.isOfType(module)) {
        pluginArtifactTargetIds.add(module.getName()+":plugin"); //NON-NLS
      }
    }

    if (pluginArtifactTargetIds.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.singletonList(CmdlineProtoUtil.createTargetsScope(ArtifactBuildTargetType.INSTANCE.getTypeId(), pluginArtifactTargetIds, forceBuild));
  }
}
