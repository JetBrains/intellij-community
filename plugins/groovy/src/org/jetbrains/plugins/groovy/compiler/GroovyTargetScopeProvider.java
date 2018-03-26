/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.compiler;

import com.intellij.compiler.impl.BuildTargetScopeProvider;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
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

import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
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
      filter(Condition.NOT_NULL).toList();
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
    return ContainerUtil.filter(ModuleManager.getInstance(project).getModules(), module -> containsGroovyResources(rootType, module));
  }

  static boolean containsGroovyResources(@NotNull JpsModuleSourceRootType<?> rootType, Module module) {
    return ContainerUtil
      .exists(ModuleRootManager.getInstance(module).getSourceRoots(rootType), root -> containsGroovyResources(module, root));
  }

  private static boolean containsGroovyResources(Module module, VirtualFile root) {
    return !ModuleRootManager.getInstance(module).getFileIndex().iterateContentUnderDirectory(root, file -> {
      if (!file.isDirectory() && GroovyFileType.GROOVY_FILE_TYPE == file.getFileType()) {
        return false; // found
      }
      return true;
    });
  }
}
