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
package org.jetbrains.android.compiler;

import com.intellij.compiler.impl.BuildTargetScopeProvider;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerFilter;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class AndroidTargetBuildScopeProvider extends BuildTargetScopeProvider {
  @NotNull
  @Override
  public List<TargetTypeBuildScope> getBuildTargetScopes(@NotNull CompileScope baseScope, @NotNull CompilerFilter filter, @NotNull Project project) {
    if (AndroidCompileUtil.isFullBuild(baseScope) && ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) {
      return Collections
        .singletonList(TargetTypeBuildScope.newBuilder().setTypeId(AndroidCommonUtils.PROJECT_BUILD_TARGET_TYPE_ID).setAllTargets(true).build());
    }
    return Collections.emptyList();
  }
}
