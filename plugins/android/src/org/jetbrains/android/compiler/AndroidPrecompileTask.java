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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.options.ExcludeEntryDescription;
import com.intellij.openapi.compiler.options.ExcludedEntriesConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.android.AndroidProjectComponent;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidPrecompileTask implements CompileTask {
  private final AndroidProjectComponent myOwner;
  
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.AndroidPrecompileTask");

  public AndroidPrecompileTask(@NotNull AndroidProjectComponent owner) {
    myOwner = owner;
  }

  @Override
  public boolean execute(CompileContext context) {
    final Project project = context.getProject();
    
    myOwner.setCompilationStarted();

    ExcludedEntriesConfiguration configuration =
      ((CompilerConfigurationImpl)CompilerConfiguration.getInstance(project)).getExcludedEntriesConfiguration();

    Set<ExcludeEntryDescription> addedEntries = new HashSet<ExcludeEntryDescription>();

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      final AndroidFacet facet = AndroidFacet.getInstance(module);

      if (facet == null) {
        continue;
      }

      final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      ApplicationManager.getApplication().invokeAndWait(new Runnable() {
        @Override
        public void run() {
          AndroidCompileUtil.createGenModulesAndSourceRoots(facet);
        }
      }, indicator != null ? indicator.getModalityState() : ModalityState.NON_MODAL);

      if (context.isRebuild()) {
        clearGenRootsAndResCache(facet, context);
      }

      final AndroidPlatform platform = facet.getConfiguration().getAndroidPlatform();
      final int platformToolsRevision = platform != null ? platform.getSdk().getPlatformToolsRevision() : -1;

      LOG.info("Platform-tools revision for module {0} is " + module.getName());

      if (platformToolsRevision >= 0 && platformToolsRevision <= 7) {
        if (facet.getConfiguration().LIBRARY_PROJECT) {
          LOG.info("Excluded sources of module " + module.getName());
          excludeAllSourceRoots(module, configuration, addedEntries);
        }
      }
    }

    if (addedEntries.size() > 0) {
      LOG.info("Files excluded by Android: " + addedEntries.size());
      CompilerManager.getInstance(project).addCompilationStatusListener(new MyCompilationStatusListener(project, addedEntries), project);
    }

    CompilerManager.getInstance(project).addCompilationStatusListener(new CompilationStatusListener() {
      @Override
      public void compilationFinished(boolean aborted, int errors, int warnings, final CompileContext compileContext) {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            myOwner.setCompilationFinished();
          }
        });
      }
    }, project);

    return true;
  }
  
  private static void clearGenRootsAndResCache(@NotNull AndroidFacet facet, @NotNull CompileContext context) {
    final Module module = facet.getModule();
    
    removeAllPackages(AndroidRootUtil.getRenderscriptGenSourceRootPath(module), context);
    removeAllPackages(facet.getAptGenSourceRootPath(), context);
    removeAllPackages(facet.getAidlGenSourceRootPath(), context);

    final String dirPath = AndroidCompileUtil.findResourcesCacheDirectory(module, false, null);
    if (dirPath != null) {
      final File dir = new File(dirPath);
      if (dir.exists()) {
        FileUtil.delete(dir);
      }
    }
  }
  
  private static void removeAllPackages(@Nullable String sourceRootPath, @NotNull CompileContext context) {
    final File sourceRoot = new File(sourceRootPath);

    final File[] children = sourceRoot.listFiles();

    if (children != null) {
      for (File child : children) {
        if (child.isDirectory() &&
            child.getName() != null &&
            StringUtil.isJavaIdentifier(child.getName())) {

          if (!FileUtil.delete(child)) {
            context.addMessage(CompilerMessageCategory.ERROR, "Cannot delete file " + child.getAbsolutePath(),
                               null, -1, -1);
          }
        }
      }
    }
    final VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(sourceRoot);

    if (vFile != null) {
      vFile.refresh(false, true);
    }
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
