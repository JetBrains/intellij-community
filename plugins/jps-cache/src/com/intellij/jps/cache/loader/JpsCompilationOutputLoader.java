package com.intellij.jps.cache.loader;

import com.intellij.jps.cache.client.JpsServerClient;
import com.intellij.jps.cache.ui.SegmentedProgressIndicatorManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

class JpsCompilationOutputLoader implements JpsOutputLoader {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.loader.JpsCompilationOutputLoader");
  protected final JpsServerClient myClient;
  protected final Project myProject;
  protected final String myProjectPath;
  protected List<File> myOldModulesPaths;
  protected Map<File, String> myTmpFolderToModuleName;

  JpsCompilationOutputLoader(JpsServerClient client, Project project) {
    myClient = client;
    myProject = project;
    myProjectPath = myProject.getBasePath();
  }

  @Override
  public LoaderStatus load(@NotNull JpsLoaderContext context) {
    myOldModulesPaths = null;
    myTmpFolderToModuleName = null;

    SegmentedProgressIndicatorManager progressIndicatorManager = context.getIndicatorManager();
    progressIndicatorManager.setText(this, "Calculating affected modules");
    List<AffectedModule> affectedModules = getAffectedModules(context.getCurrentSourcesState(), context.getCommitSourcesState());
    progressIndicatorManager.finished(this);
    progressIndicatorManager.getProgressIndicator().checkCanceled();

    if (affectedModules.size() > 0) {
      Pair<Boolean, Map<File, String>> downloadResultsPair = myClient.downloadCompiledModules(progressIndicatorManager, affectedModules);
      myTmpFolderToModuleName = downloadResultsPair.second;
      if (!downloadResultsPair.first) return LoaderStatus.FAILED;
    }
    else {
      // Move progress up to the half of segment size
      progressIndicatorManager.setTasksCount(1);
      progressIndicatorManager.updateFraction(1.0);
    }
    return LoaderStatus.COMPLETE;
  }

  @Override
  public void rollback() {
    if (myTmpFolderToModuleName == null) return;
    myTmpFolderToModuleName.forEach((tmpFolder, __) -> {
      if (tmpFolder.isDirectory() && tmpFolder.exists()) FileUtil.delete(tmpFolder);
    });
    LOG.debug("JPS cache loader rolled back");
  }

  @Override
  public void apply() {
    if (myOldModulesPaths != null) {
      LOG.debug("Removing old compilation outputs " + myOldModulesPaths.size() + " counts");
      myOldModulesPaths.forEach(file -> { if (file.exists()) FileUtil.delete(file); });
    }

    if (myTmpFolderToModuleName == null) {
      LOG.debug("Nothing to apply, download results are empty");
      return;
    }

    myTmpFolderToModuleName.forEach((tmpModuleFolder, moduleName) -> {
      File currentModuleBuildDir = new File(tmpModuleFolder.getParentFile(), moduleName);
      FileUtil.delete(currentModuleBuildDir);
      try {
        FileUtil.rename(tmpModuleFolder, currentModuleBuildDir);
        LOG.debug("Module: " + moduleName + " was replaced successfully");
      }
      catch (IOException e) {
        LOG.warn("Couldn't replace compilation output for module: " + moduleName, e);
      }
    });
  }

  protected List<AffectedModule> getAffectedModules(@Nullable Map<String, Map<String, BuildTargetState>> currentModulesState,
                                                    @NotNull Map<String, Map<String, BuildTargetState>> commitModulesState) {
    long start = System.currentTimeMillis();

    List<AffectedModule> affectedModules = new ArrayList<>();
    myOldModulesPaths = new ArrayList<>();

    if (currentModulesState == null) {
      commitModulesState.forEach((type, map) -> {
        map.forEach((name, state) -> {
          affectedModules.add(new AffectedModule(type, name, state.getHash(), getProjectRelativeFile(state.getRelativePath())));
        });
      });
      LOG.warn("Project doesn't contain metadata, force to download " + affectedModules.size() + " modules.");
      return affectedModules;
    }

    // Add new build types
    Set<String> newBuildTypes = new HashSet<>(commitModulesState.keySet());
    newBuildTypes.removeAll(currentModulesState.keySet());
    newBuildTypes.forEach(type -> {
      commitModulesState.get(type).forEach((name, state) -> {
        affectedModules.add(new AffectedModule(type, name, state.getHash(), getProjectRelativeFile(state.getRelativePath())));
      });
    });

    // Calculate old paths for remove
    Set<String> oldBuildTypes = new HashSet<>(currentModulesState.keySet());
    oldBuildTypes.removeAll(commitModulesState.keySet());
    oldBuildTypes.forEach(type -> {
      currentModulesState.get(type).forEach((name, state) -> {
        myOldModulesPaths.add(getProjectRelativeFile(state.getRelativePath()));
      });
    });

    commitModulesState.forEach((type, map) -> {
      Map<String, BuildTargetState> currentTypeState = currentModulesState.get(type);

      // Add new build modules
      Set<String> newBuildModules = new HashSet<>(map.keySet());
      newBuildModules.removeAll(currentTypeState.keySet());
      newBuildModules.forEach(name -> {
        BuildTargetState state = map.get(name);
        affectedModules.add(new AffectedModule(type, name, state.getHash(), getProjectRelativeFile(state.getRelativePath())));
      });

      // Calculate old modules paths for remove
      Set<String> oldBuildModules = new HashSet<>(currentTypeState.keySet());
      oldBuildModules.removeAll(map.keySet());
      oldBuildModules.forEach(name -> {
        BuildTargetState state = currentTypeState.get(name);
        myOldModulesPaths.add(getProjectRelativeFile(state.getRelativePath()));
      });

      // In another case compare modules inside the same build type
      map.forEach((name, state) -> {
        BuildTargetState currentTargetState = currentTypeState.get(name);
        if (currentTargetState == null || !state.equals(currentTargetState)) {
          affectedModules.add(new AffectedModule(type, name, state.getHash(), getProjectRelativeFile(state.getRelativePath())));
          return;
        }

        File outFile = getProjectRelativeFile(state.getRelativePath());
        if (!outFile.exists()) {
          affectedModules.add(new AffectedModule(type, name, state.getHash(), outFile));
        }
      });
    });
    long total = System.currentTimeMillis() - start;
    LOG.debug("Compilation output affected for the " + affectedModules.size() + " modules. Computation took " + total + "ms");
    return affectedModules;
  }

  private File getProjectRelativeFile(String projectRelativePath) {
    return new File(myProjectPath, projectRelativePath);
  }
}