/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.openapi.compiler.CompilationStatusListener;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.options.ExcludeEntryDescription;
import com.intellij.openapi.compiler.options.ExcludedEntriesConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidFacet;

import java.util.Collection;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidPrecompileTask implements CompileTask {
  @Override
  public boolean execute(CompileContext context) {
    final Project project = context.getProject();

    ExcludedEntriesConfiguration configuration =
      ((CompilerConfigurationImpl)CompilerConfiguration.getInstance(project)).getExcludedEntriesConfiguration();

    Set<ExcludeEntryDescription> addedEntries = new HashSet<ExcludeEntryDescription>();

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);

      if (facet != null && facet.getConfiguration().LIBRARY_PROJECT) {
        excludeAllSourceRoots(module, configuration, addedEntries);
      }
    }

    CompilerManager.getInstance(project).addCompilationStatusListener(new MyCompilationStatusListener(project, addedEntries), project);

    return true;
  }

  private static void excludeAllSourceRoots(Module module,
                                            ExcludedEntriesConfiguration configuration,
                                            Collection<ExcludeEntryDescription> addedEntries) {
    Project project = module.getProject();
    VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();

    for (VirtualFile sourceRoot : sourceRoots) {
      ExcludeEntryDescription description = new ExcludeEntryDescription(sourceRoot, true, false, project);

      if (!configuration.containsExcludeEntryDescription(description)) {
        configuration.addExcludeEntryDescription(description);
        addedEntries.add(description);
      }
    }
  }

  private static class MyCompilationStatusListener implements CompilationStatusListener {
    private final Project myProject;
    private final Set<ExcludeEntryDescription> myEntriesToRemove;

    public MyCompilationStatusListener(Project project, Set<ExcludeEntryDescription> entriesToRemove) {
      myProject = project;
      myEntriesToRemove = entriesToRemove;
    }

    @Override
    public void compilationFinished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
      CompilerManager.getInstance(myProject).removeCompilationStatusListener(this);

      ExcludedEntriesConfiguration configuration =
        ((CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject)).getExcludedEntriesConfiguration();
      ExcludeEntryDescription[] descriptions = configuration.getExcludeEntryDescriptions();

      configuration.removeAllExcludeEntryDescriptions();

      for (ExcludeEntryDescription description : descriptions) {
        if (!myEntriesToRemove.contains(description)) {
          configuration.addExcludeEntryDescription(description);
        }
      }
    }
  }
}
