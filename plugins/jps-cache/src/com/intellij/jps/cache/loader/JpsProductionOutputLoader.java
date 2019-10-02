package com.intellij.jps.cache.loader;

import com.intellij.jps.cache.client.JpsServerClient;
import com.intellij.jps.cache.hashing.PersistentCachingModuleHashingService;
import com.intellij.jps.cache.ui.SegmentedProgressIndicatorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Map;

class JpsProductionOutputLoader extends JpsCompilationOutputLoader {
  JpsProductionOutputLoader(JpsServerClient client, Project project, PersistentCachingModuleHashingService hashingService) {
    super(client, project, hashingService);
  }

  @Override
  LoaderStatus load(@NotNull File compilerOutputDir, @NotNull SegmentedProgressIndicatorManager progressIndicatorManager) {
    File productionDir = new File(compilerOutputDir, CompilerModuleExtension.PRODUCTION);
    progressIndicatorManager.setText(this, "Calculating affected production modules");
    Map<String, String> affectedProductionModules = getAffectedModules(productionDir, myHashingService::getAffectedProduction,
                                                                       myHashingService::computeProductionHashesForProject);
    progressIndicatorManager.finished(this);
    progressIndicatorManager.getProgressIndicator().checkCanceled();
    if (affectedProductionModules.size() > 0) {
      FileUtil.createDirectory(productionDir);
      Pair<Boolean, Map<File, String>> downloadResultsPair = myClient.downloadCompiledModules(myProject, progressIndicatorManager,
                                                                                              CompilerModuleExtension.PRODUCTION,
                                                                                              affectedProductionModules, productionDir);
      myTmpFolderToModuleName = downloadResultsPair.second;
      if (!downloadResultsPair.first) return LoaderStatus.FAILED;
    } else {
      // Move progress up to the half of segment size
      displaySkippedStepOnProgressBar(progressIndicatorManager);
    }
    return LoaderStatus.COMPLETE;
  }
}