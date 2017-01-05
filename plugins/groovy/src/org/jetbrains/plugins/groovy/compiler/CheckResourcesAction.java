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

import com.google.common.annotations.VisibleForTesting;
import com.intellij.compiler.impl.ProjectCompileScope;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.groovy.GroovyResourceChecker;
import org.jetbrains.jps.model.java.JavaResourceRootType;

/**
 * @author peter
 */
public abstract class CheckResourcesAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    checkResources(e.getProject(), null, this instanceof Rebuild);
  }

  @VisibleForTesting
  public static void checkResources(Project project, @Nullable CompileStatusNotification callback, boolean rebuild) {
    ProjectCompileScope scope = new ProjectCompileScope(project);
    scope.putUserData(GroovyResourceChecker.CHECKING_RESOURCES_REBUILD, rebuild);
    CompilerManager.getInstance(project).make(scope, callback);
  }

  public static class Group extends DefaultActionGroup {
    @Override
    public void update(AnActionEvent e) {
      Project project = e.getProject();
      e.getPresentation().setEnabledAndVisible(project != null && containsGroovyResources(project));
    }

    private static boolean containsGroovyResources(Project project) {
      return CachedValuesManager.getManager(project).getCachedValue(project, () ->
        CachedValueProvider.Result.create(calcContainsGroovyResources(project),
                                          ProjectRootModificationTracker.getInstance(project), VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS));
    }

    private static boolean calcContainsGroovyResources(Project project) {
      return ContainerUtil.exists(ModuleManager.getInstance(project).getModules(), module ->
        GroovyTargetScopeProvider.containsGroovyResources(JavaResourceRootType.RESOURCE, module) ||
        GroovyTargetScopeProvider.containsGroovyResources(JavaResourceRootType.TEST_RESOURCE, module));
    }
  }

  public static class Make extends CheckResourcesAction { }
  public static class Rebuild extends CheckResourcesAction { }
}
