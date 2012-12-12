package org.jetbrains.android.compiler;

import com.intellij.compiler.impl.BuildTargetScopeProvider;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerFilter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidBuildTargetScopeProvider extends BuildTargetScopeProvider {
  @NotNull
  @Override
  public List<TargetTypeBuildScope> getBuildTargetScopes(
    @NotNull CompileScope baseScope, @NotNull CompilerFilter filter, @NotNull Project project) {

    if (!ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) {
      return Collections.emptyList();
    }
    final List<String> dexTargetIds = new ArrayList<String>();
    final List<String> packagingTargetIds = new ArrayList<String>();
    final boolean fullBuild = AndroidCompileUtil.isFullBuild(baseScope);

    for (Module module : baseScope.getAffectedModules()) {
      final AndroidFacet facet = AndroidFacet.getInstance(module);

      if (facet == null) {
        continue;
      }
      // todo: make AndroidPackagingBuilder fully target-based and change this
      packagingTargetIds.add(module.getName());

      if (fullBuild && !facet.getConfiguration().LIBRARY_PROJECT) {
        dexTargetIds.add(module.getName());
      }
    }
    return Arrays.asList(
      TargetTypeBuildScope.newBuilder().setTypeId(AndroidCommonUtils.DEX_BUILD_TARGET_TYPE_ID).
        addAllTargetId(dexTargetIds).build(),
      TargetTypeBuildScope.newBuilder().setTypeId(AndroidCommonUtils.PACKAGING_BUILD_TARGET_TYPE_ID).
        addAllTargetId(packagingTargetIds).build());
  }
}
