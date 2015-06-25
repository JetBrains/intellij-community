/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.devkit.build;

import com.intellij.compiler.impl.BuildTargetScopeProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerFilter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.util.PsiUtil;
import org.jetbrains.jps.api.CmdlineProtoUtil;
import org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;
import org.jetbrains.jps.devkit.builder.RuntimeModuleDescriptorsTarget;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTargetType;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class PluginModuleBuildScopeProvider extends BuildTargetScopeProvider {
  @NotNull
  @Override
  public List<TargetTypeBuildScope> getBuildTargetScopes(@NotNull CompileScope baseScope, @NotNull CompilerFilter filter,
                                                         @NotNull final Project project, boolean forceBuild) {
    List<String> pluginArtifactTargetIds = new ArrayList<String>();
    for (Module module : baseScope.getAffectedModules()) {
      if (PluginModuleType.isOfType(module)) {
        pluginArtifactTargetIds.add(module.getName()+":plugin");
      }
    }

    List<TargetTypeBuildScope> scopes = new ArrayList<TargetTypeBuildScope>();
    if (!pluginArtifactTargetIds.isEmpty()) {
      scopes.add(CmdlineProtoUtil.createTargetsScope(ArtifactBuildTargetType.INSTANCE.getTypeId(), pluginArtifactTargetIds, forceBuild));
    }
    //noinspection UnresolvedPropertyKey
    if (Registry.is("ide.new.loader.generate.dependencies", true) && new ReadAction<Boolean>() {
      protected void run(final Result<Boolean> result) {
        result.setResult(PsiUtil.isIdeaProject(project));
      }
    }.execute().getResultObject()) {
      scopes.add(CmdlineProtoUtil.createAllTargetsScope(RuntimeModuleDescriptorsTarget.TARGET_TYPE, forceBuild));
    }
    return scopes;
  }
}
