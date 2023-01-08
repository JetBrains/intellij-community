// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.compiler;

import com.intellij.compiler.impl.BuildTargetScopeProvider;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScopes;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.CmdlineProtoUtil;
import org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;
import org.jetbrains.jps.incremental.groovy.CheckResourcesTarget;
import org.jetbrains.jps.incremental.groovy.GroovyResourceChecker;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.jetbrains.plugins.groovy.GroovyFileType;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class GroovyTargetScopeProvider extends BuildTargetScopeProvider {
  @NotNull
  @Override
  public List<TargetTypeBuildScope> getBuildTargetScopes(@NotNull CompileScope baseScope,
                                                         @NotNull Project project,
                                                         boolean forceBuild) {
    Boolean checkResourcesRebuild = baseScope.getUserData(GroovyResourceChecker.CHECKING_RESOURCES_REBUILD);
    if (checkResourcesRebuild == null) return Collections.emptyList();

    forceBuild |= checkResourcesRebuild;
    return JBIterable.of(createTargets(project, forceBuild, JavaResourceRootType.RESOURCE, CheckResourcesTarget.PRODUCTION),
                         createTargets(project, forceBuild, JavaResourceRootType.TEST_RESOURCE, CheckResourcesTarget.TESTS)).
      filter(Conditions.notNull()).toList();
  }

  @Nullable
  private static TargetTypeBuildScope createTargets(@NotNull Project project,
                                                    boolean forceBuild,
                                                    final JavaResourceRootType rootType,
                                                    final CheckResourcesTarget.Type targetType) {
    List<Module> withResources = getModulesWithGroovyResources(project, rootType);
    return withResources.isEmpty()
           ? null
           : CmdlineProtoUtil.createTargetsScope(targetType.getTypeId(), ContainerUtil.map(withResources, Module::getName), forceBuild);
  }

  private static List<Module> getModulesWithGroovyResources(@NotNull Project project, @NotNull JpsModuleSourceRootType<?> rootType) {
    return ContainerUtil.filter(ModuleManager.getInstance(project).getModules(), module -> containsGroovyResources(rootType, module, false));
  }

  static boolean containsGroovyResources(@NotNull JpsModuleSourceRootType<?> rootType, Module module, boolean isSmartMode) {
    return ContainerUtil
      .exists(ModuleRootManager.getInstance(module).getSourceRoots(rootType), root -> containsGroovyResources(module, root, isSmartMode));
  }

  private static boolean containsGroovyResources(Module module, VirtualFile root, boolean isSmartMode) {
    if (isSmartMode) {
      Collection<?> files = FileTypeIndex.getFiles(GroovyFileType.GROOVY_FILE_TYPE, GlobalSearchScopes.directoryScope(module.getProject(), root, true));
      return !files.isEmpty();
    } else {
      return !ModuleRootManager.getInstance(module).getFileIndex().iterateContentUnderDirectory(root, file -> {
        if (!file.isDirectory() && FileTypeRegistry.getInstance().isFileOfType(file, GroovyFileType.GROOVY_FILE_TYPE)) {
          return false; // found
        }
        return true;
      });
    }
  }
}
