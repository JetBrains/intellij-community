package com.intellij.jps.cache.loader;

import com.intellij.jps.cache.client.JpsServerClient;
import com.intellij.jps.cache.ui.SegmentedProgressIndicatorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Map;

class JpsTestOutputLoader extends JpsCompilationOutputLoader {
  JpsTestOutputLoader(JpsServerClient client, Project project) {
    super(client, project);
  }

  @Override
  LoaderStatus load(@NotNull File compilerOutputDir, @NotNull JpsLoaderContext context) {
    File testDir = new File(compilerOutputDir, CompilerModuleExtension.TEST);
    SegmentedProgressIndicatorManager progressIndicatorManager = context.getIndicatorManager();
    progressIndicatorManager.setText(this, "Calculating affected test modules");
    Map<String, String> affectedTestModules = getAffectedModules(testDir, context.getCurrentSourcesState() != null
                                                                          ? context.getCurrentSourcesState().getTest()
                                                                          : null,
                                                                 context.getCommitSourcesState().getTest());
    progressIndicatorManager.finished(this);
    progressIndicatorManager.getProgressIndicator().checkCanceled();
    if (affectedTestModules.size() > 0) {
      FileUtil.createDirectory(testDir);
      Pair<Boolean, Map<File, String>> downloadResultsPair = myClient.downloadCompiledModules(myProject, progressIndicatorManager,
                                                                                              CompilerModuleExtension.TEST,
                                                                                              affectedTestModules, testDir);
      myTmpFolderToModuleName = downloadResultsPair.second;
      if (!downloadResultsPair.first) return LoaderStatus.FAILED;
    }
    else {
      // Move progress up to the half of segment size
      displaySkippedStepOnProgressBar(progressIndicatorManager);
    }
    return LoaderStatus.COMPLETE;
  }
}