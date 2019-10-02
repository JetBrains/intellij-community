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

class JpsTestOutputLoader extends JpsCompilationOutputLoader {
  JpsTestOutputLoader(JpsServerClient client, Project project, PersistentCachingModuleHashingService hashingService) {
    super(client, project, hashingService);
  }

  @Override
  LoaderStatus load(@NotNull File compilerOutputDir, @NotNull SegmentedProgressIndicatorManager progressIndicatorManager) {
    File testDir = new File(compilerOutputDir, CompilerModuleExtension.TEST);
    progressIndicatorManager.setText(this, "Calculating affected test modules");
    Map<String, String> affectedTestModules = getAffectedModules(testDir, myHashingService::getAffectedTests,
                                                                 myHashingService::computeTestHashesForProject);
    progressIndicatorManager.finished(this);
    progressIndicatorManager.getProgressIndicator().checkCanceled();
    if (affectedTestModules.size() > 0) {
      FileUtil.createDirectory(testDir);
      Pair<Boolean, Map<File, String>> downloadResultsPair = myClient.downloadCompiledModules(myProject, progressIndicatorManager,
                                                                                              CompilerModuleExtension.TEST,
                                                                                              affectedTestModules, testDir);
      myTmpFolderToModuleName = downloadResultsPair.second;
      if (!downloadResultsPair.first) return LoaderStatus.FAILED;
    } else {
      // Move progress up to the half of segment size
      displaySkippedStepOnProgressBar(progressIndicatorManager);
    }
    return LoaderStatus.COMPLETE;
  }
}