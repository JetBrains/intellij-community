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
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.facet.ProjectFacetManager;
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
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.impl.compiler.ArtifactCompileScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.android.compiler.artifact.AndroidApplicationArtifactProperties;
import org.jetbrains.android.compiler.artifact.AndroidArtifactPropertiesProvider;
import org.jetbrains.android.compiler.artifact.AndroidArtifactSigningMode;
import org.jetbrains.android.compiler.artifact.AndroidArtifactUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.maven.AndroidMavenUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidPrecompileTask implements CompileTask {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.AndroidPrecompileTask");

  @Override
  public boolean execute(CompileContext context) {
    final Project project = context.getProject();

    // todo: add gen roots to excludes in android-jps and then remove this line
    createGenModulesAndSourceRoots(project);

    if (!CompilerWorkspaceConfiguration.getInstance(project).useOutOfProcessBuild()) {
      return prepareForCompilation(context);
    }
    return true;
  }

  private static boolean prepareForCompilation(CompileContext context) {
    final Project project = context.getProject();

    if (!checkArtifacts(context)) {
      return false;
    }
    checkAndroidDependencies(context);

    ExcludedEntriesConfiguration configuration =
      CompilerConfiguration.getInstance(project).getExcludedEntriesConfiguration();

    Set<ExcludeEntryDescription> addedEntries = new HashSet<ExcludeEntryDescription>();

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      final AndroidFacet facet = AndroidFacet.getInstance(module);

      if (facet == null) {
        continue;
      }

      if (context.isRebuild()) {
        clearResCache(facet, context);
      }

      final AndroidPlatform platform = facet.getConfiguration().getAndroidPlatform();
      final int platformToolsRevision = platform != null ? platform.getSdkData().getPlatformToolsRevision() : -1;

      LOG.debug("Platform-tools revision for module " + module.getName() + " is " + platformToolsRevision);

      if (facet.getConfiguration().LIBRARY_PROJECT) {
        if (platformToolsRevision >= 0 && platformToolsRevision <= 7) {
          LOG.debug("Excluded sources of module " + module.getName());
          excludeAllSourceRoots(module, configuration, addedEntries);
        }
        else {
          // todo: support this by project converter to use on compile-server
          unexcludeAllSourceRoots(facet, configuration);
        }
      }
    }

    if (addedEntries.size() > 0) {
      LOG.debug("Files excluded by Android: " + addedEntries.size());
      CompilerManager.getInstance(project).addCompilationStatusListener(new MyCompilationStatusListener(project, addedEntries), project);
    }
    return true;
  }

  private static void createGenModulesAndSourceRoots(Project project) {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      final AndroidFacet facet = AndroidFacet.getInstance(module);

      if (facet != null) {
        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
          @Override
          public void run() {
            AndroidCompileUtil.createGenModulesAndSourceRoots(facet);
          }
        }, indicator != null ? indicator.getModalityState() : ModalityState.NON_MODAL);
      }
    }
  }

  private static boolean checkArtifacts(@NotNull CompileContext context) {
    final Project project = context.getProject();
    if (!ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) {
      return true;
    }
    final CompileScope scope = context.getCompileScope();

    final Set<Artifact> artifacts = ApplicationManager.getApplication().runReadAction(new Computable<Set<Artifact>>() {
      @Override
      public Set<Artifact> compute() {
        return ArtifactCompileScope.getArtifactsToBuild(project, scope, false);
      }
    });
    if (artifacts == null) {
      return true;
    }
    final Set<Artifact> debugArtifacts = new HashSet<Artifact>();
    final Set<Artifact> releaseArtifacts = new HashSet<Artifact>();
    final Map<AndroidFacet, List<Artifact>> facet2artifacts = new HashMap<AndroidFacet, List<Artifact>>();

    for (final Artifact artifact : artifacts) {
      final ArtifactProperties<?> properties = artifact.getProperties(AndroidArtifactPropertiesProvider.getInstance());
      if (properties instanceof AndroidApplicationArtifactProperties) {
        final AndroidArtifactSigningMode mode = ((AndroidApplicationArtifactProperties)properties).getSigningMode();
        if (mode == AndroidArtifactSigningMode.DEBUG) {
          debugArtifacts.add(artifact);
        }
        else {
          releaseArtifacts.add(artifact);
        }
      }

      final AndroidFacet facet = ApplicationManager.getApplication().runReadAction(new Computable<AndroidFacet>() {
        @Nullable
        @Override
        public AndroidFacet compute() {
          return AndroidArtifactUtil.getPackagedFacet(project, artifact);
        }
      });
      if (facet != null) {
        List<Artifact> list = facet2artifacts.get(facet);
        if (list == null) {
          list = new ArrayList<Artifact>();
          facet2artifacts.put(facet, list);
        }
        list.add(artifact);
      }
    }
    boolean success = true;

    if (debugArtifacts.size() > 0 && releaseArtifacts.size() > 0) {
      final String message = "Cannot build debug and release Android artifacts in the same session\n" +
                             "Debug artifacts: " + toString(debugArtifacts) + "\n" +
                             "Release artifacts: " + toString(releaseArtifacts);
      context.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1);
      success = false;
    }

    if (releaseArtifacts.size() > 0 &&
        CompileStepBeforeRun.getRunConfiguration(context) != null) {
      final String message = "Cannot build release Android artifacts in the 'make before run' session\n" +
                             "Release artifacts: " + toString(releaseArtifacts);
      context.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1);
      success = false;
    }

    for (Map.Entry<AndroidFacet, List<Artifact>> entry : facet2artifacts.entrySet()) {
      final List<Artifact> list = entry.getValue();
      final String moduleName = entry.getKey().getModule().getName();

      if (list.size() > 1) {
        final Artifact firstArtifact = list.get(0);
        final Object[] firstArtifactProGuardOptions = getProGuardOptions(firstArtifact);

        for (int i = 1; i < list.size(); i++) {
          final Artifact artifact = list.get(i);
          if (!Arrays.equals(getProGuardOptions(artifact), firstArtifactProGuardOptions)) {
            context.addMessage(CompilerMessageCategory.ERROR, "Artifacts related to the same module '" +
                                                              moduleName +
                                                              "' have different ProGuard options: " +
                                                              firstArtifact.getName() +
                                                              ", " +
                                                              artifact.getName(), null, -1, -1);
            success = false;
            break;
          }
        }
      }
    }

    return success;
  }

  @NotNull
  private static Object[] getProGuardOptions(@NotNull Artifact artifact) {
    final ArtifactProperties<?> properties = artifact.getProperties(AndroidArtifactPropertiesProvider.getInstance());
    if (properties instanceof AndroidApplicationArtifactProperties) {
      final AndroidApplicationArtifactProperties p = (AndroidApplicationArtifactProperties)properties;
      return new Object[] {p.isRunProGuard(), p.getProGuardCfgFileUrl(), p.isIncludeSystemProGuardCfgFile()};
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  private static String toString(Collection<Artifact> artifacts) {
    final StringBuilder result = new StringBuilder();
    for (Artifact artifact : artifacts) {
      if (result.length() > 0) {
        result.append(", ");
      }
      result.append(artifact.getName());
    }
    return result.toString();
  }

  private static void checkAndroidDependencies(@NotNull CompileContext context) {
    for (Module module : context.getCompileScope().getAffectedModules()) {
      final AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet == null) {
        continue;
      }

      final Pair<String, VirtualFile> manifestMergerProp =
        AndroidRootUtil.getProjectPropertyValue(module, AndroidCommonUtils.ANDROID_MANIFEST_MERGER_PROPERTY);
      if (manifestMergerProp != null && Boolean.parseBoolean(manifestMergerProp.getFirst())) {
        context.addMessage(CompilerMessageCategory.WARNING,
                           "[" + module.getName() + "] " + AndroidBundle.message("android.manifest.merger.not.supported.error"),
                           manifestMergerProp.getSecond().getUrl(), -1, -1);
      }

      if (!facet.getConfiguration().LIBRARY_PROJECT) {

        for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
          if (entry instanceof ModuleOrderEntry) {
            final ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)entry;

            if (moduleOrderEntry.getScope() == DependencyScope.COMPILE) {
              final Module depModule = moduleOrderEntry.getModule();

              if (depModule != null) {
                final AndroidFacet depFacet = AndroidFacet.getInstance(depModule);

                if (depFacet != null && !depFacet.getConfiguration().LIBRARY_PROJECT) {
                  String message = "Suspicious module dependency " +
                                   module.getName() +
                                   " -> " +
                                   depModule.getName() +
                                   ": Android application module depends on other application module. Possibly, you should ";
                  if (AndroidMavenUtil.isMavenizedModule(depModule)) {
                    message += "change packaging type of module " + depModule.getName() + " to 'apklib' in pom.xml file or ";
                  }
                  message += "change dependency scope to 'Provided'.";
                  context.addMessage(CompilerMessageCategory.WARNING, message, null, -1, -1);
                }
              }
            }
          }
        }
      }
    }
  }

  private static void clearResCache(@NotNull AndroidFacet facet, @NotNull CompileContext context) {
    final Module module = facet.getModule();

    final String dirPath = AndroidCompileUtil.findResourcesCacheDirectory(module, false, null);
    if (dirPath != null) {
      final File dir = new File(dirPath);
      if (dir.exists()) {
        FileUtil.delete(dir);
      }
    }
  }

  private static void unexcludeAllSourceRoots(AndroidFacet facet,
                                              ExcludedEntriesConfiguration configuration) {
    final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(facet.getModule()).getSourceRoots();
    final Set<VirtualFile> sourceRootSet = new HashSet<VirtualFile>();
    sourceRootSet.addAll(Arrays.asList(sourceRoots));

    final String aidlGenSourceRootPath = AndroidRootUtil.getAidlGenSourceRootPath(facet);
    if (aidlGenSourceRootPath != null) {
      final VirtualFile aidlGenSourceRoot = LocalFileSystem.getInstance().findFileByPath(aidlGenSourceRootPath);

      if (aidlGenSourceRoot != null) {
        sourceRootSet.remove(aidlGenSourceRoot);
      }
    }

    final String aptGenSourceRootPath = AndroidRootUtil.getAptGenSourceRootPath(facet);
    if (aptGenSourceRootPath != null) {
      final VirtualFile aptGenSourceRoot = LocalFileSystem.getInstance().findFileByPath(aptGenSourceRootPath);

      if (aptGenSourceRoot != null) {
        sourceRootSet.remove(aptGenSourceRoot);
      }
    }

    final VirtualFile rsGenRoot = AndroidRootUtil.getRenderscriptGenDir(facet);
    if (rsGenRoot != null) {
      sourceRootSet.remove(rsGenRoot);
    }

    final VirtualFile buildconfigGenDir = AndroidRootUtil.getBuildconfigGenDir(facet);
    if (buildconfigGenDir != null) {
      sourceRootSet.remove(buildconfigGenDir);
    }

    final ExcludeEntryDescription[] descriptions = configuration.getExcludeEntryDescriptions();
    configuration.removeAllExcludeEntryDescriptions();

    for (ExcludeEntryDescription description : descriptions) {
      final VirtualFile file = description.getVirtualFile();

      if (file == null || !sourceRootSet.contains(file)) {
        configuration.addExcludeEntryDescription(description);
      }
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

  private static class MyCompilationStatusListener extends CompilationStatusAdapter {
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
