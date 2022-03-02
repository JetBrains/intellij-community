package com.intellij.coverage;

import com.intellij.coverage.view.CoverageView;
import com.intellij.coverage.view.CoverageViewManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman.Chernyatchik
 */
public abstract class BaseCoverageAnnotator implements CoverageAnnotator {

  private final Project myProject;
  private volatile boolean myIsLoading = false;

  public boolean isLoading() {
    return myIsLoading;
  }

  @Nullable
  protected abstract Runnable createRenewRequest(@NotNull final CoverageSuitesBundle suite, @NotNull final CoverageDataManager dataManager);

  public BaseCoverageAnnotator(final Project project) {
    myProject = project;
  }

  @Override
  public void onSuiteChosen(CoverageSuitesBundle newSuite) {
    myIsLoading = false;
  }

  @Override
  public void renewCoverageData(@NotNull final CoverageSuitesBundle suite, @NotNull final CoverageDataManager dataManager) {
    final Runnable request = createRenewRequest(suite, dataManager);
    if (request != null) {
      if (myProject.isDisposed()) return;
      ProgressManager.getInstance().run(new Task.Backgroundable(myProject, CoverageBundle.message("coverage.view.loading.data"), true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          myIsLoading = true;
          try {
            request.run();
          }
          finally {
            myIsLoading = false;
          }
        }

        @Override
        public void onSuccess() {
          final CoverageView coverageView = CoverageViewManager.getInstance(myProject).getToolwindow(suite);
          if (coverageView != null) {
            coverageView.resetView();
          }
        }

        @Override
        public void onCancel() {
          super.onCancel();
          CoverageDataManager.getInstance(myProject).chooseSuitesBundle(null);
        }
      });
    }
  }

  public Project getProject() {
    return myProject;
  }

  public static class FileCoverageInfo {
    public int totalLineCount;
    public int coveredLineCount;
  }

  public static class DirCoverageInfo extends FileCoverageInfo {
    public int totalFilesCount;
    public int coveredFilesCount;
  }
}
