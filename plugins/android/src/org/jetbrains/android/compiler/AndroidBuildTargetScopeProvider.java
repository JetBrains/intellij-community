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
import org.jetbrains.jps.api.CmdlineProtoUtil;

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
  public List<TargetTypeBuildScope> getBuildTargetScopes(@NotNull CompileScope baseScope, @NotNull CompilerFilter filter,
                                                         @NotNull Project project, boolean forceBuild) {

    if (!ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) {
      return Collections.emptyList();
    }
    final List<String> appTargetIds = new ArrayList<String>();
    final List<String> libTargetIds = new ArrayList<String>();
    final List<String> allTargetIds = new ArrayList<String>();
    final boolean fullBuild = AndroidCompileUtil.isFullBuild(baseScope);

    for (Module module : baseScope.getAffectedModules()) {
      final AndroidFacet facet = AndroidFacet.getInstance(module);

      if (facet == null) {
        continue;
      }
      // todo: make AndroidPackagingBuilder fully target-based and change this
      allTargetIds.add(module.getName());

      if (fullBuild) {
        if (facet.getProperties().LIBRARY_PROJECT) {
          libTargetIds.add(module.getName());
        }
        else {
          appTargetIds.add(module.getName());
        }
      }
    }
    return Arrays.asList(
      CmdlineProtoUtil.createTargetsScope(AndroidCommonUtils.DEX_BUILD_TARGET_TYPE_ID, appTargetIds, forceBuild),
      CmdlineProtoUtil.createTargetsScope(AndroidCommonUtils.RESOURCE_CACHING_BUILD_TARGET_ID, allTargetIds, forceBuild),
      CmdlineProtoUtil.createTargetsScope(AndroidCommonUtils.RESOURCE_PACKAGING_BUILD_TARGET_ID, appTargetIds, forceBuild),
      CmdlineProtoUtil.createTargetsScope(AndroidCommonUtils.PACKAGING_BUILD_TARGET_TYPE_ID, allTargetIds, forceBuild),
      CmdlineProtoUtil.createTargetsScope(AndroidCommonUtils.LIBRARY_PACKAGING_BUILD_TARGET_ID, libTargetIds, forceBuild)
    );
  }
}
