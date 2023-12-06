// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * When adding an external coverage report, this manager subscribes to the changes in the report file,
 * so coverage data can be updated after coverage rebuild externally.
 */
@Service(Service.Level.PROJECT)
public final class ExternalCoverageWatchManager {
  private final Project myProject;

  private Set<LocalFileSystem.WatchRequest> myWatchRequests;
  private List<String> myCurrentSuiteRoots;
  private final VirtualFileContentsChangedAdapter myContentListener = new VirtualFileContentsChangedAdapter() {
    @Override
    protected void onFileChange(@NotNull VirtualFile fileOrDirectory) {
      if (myCurrentSuiteRoots != null && VfsUtilCore.isUnder(fileOrDirectory.getPath(), myCurrentSuiteRoots)) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          CoverageDataManagerImpl manager = (CoverageDataManagerImpl) CoverageDataManager.getInstance(myProject);
          for (CoverageSuitesBundle bundle : manager.activeSuites()) {
            bundle.restoreCoverageData();
            manager.updateCoverageData(bundle);
          }
        });
      }
    }

    @Override
    protected void onBeforeFileChange(@NotNull VirtualFile fileOrDirectory) { }
  };

  public static ExternalCoverageWatchManager getInstance(@NotNull Project project) {
    return project.getService(ExternalCoverageWatchManager.class);
  }

  public ExternalCoverageWatchManager(Project project) { myProject = project; }

  /**
   * Called from EDT, on external coverage suite choosing
   */
  public void addRootsToWatch(List<? extends CoverageSuite> suites) {
    myCurrentSuiteRoots = ContainerUtil.map(suites, suite -> suite.getCoverageDataFileName());
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    myCurrentSuiteRoots.forEach(path -> fileSystem.refreshAndFindFileByPath(path));
    myWatchRequests = fileSystem.addRootsToWatch(myCurrentSuiteRoots, true);
    VirtualFileManager.getInstance().addVirtualFileListener(myContentListener);
  }

  public void clearWatches() {
    if (myWatchRequests == null) return;
    LocalFileSystem.getInstance().removeWatchedRoots(myWatchRequests);
    VirtualFileManager.getInstance().removeVirtualFileListener(myContentListener);

    myWatchRequests = null;
    myCurrentSuiteRoots = null;
  }
}
