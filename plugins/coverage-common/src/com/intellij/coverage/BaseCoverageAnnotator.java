package com.intellij.coverage;

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
  private boolean myHasVcsFilteredChildren;

  @Nullable
  protected abstract Runnable createRenewRequest(@NotNull final CoverageSuitesBundle suite, @NotNull final CoverageDataManager dataManager);

  public BaseCoverageAnnotator(final Project project) {
    myProject = project;
  }

  @Override
  public void onSuiteChosen(@Nullable CoverageSuitesBundle newSuite) { }

  @Override
  public void renewCoverageData(@NotNull final CoverageSuitesBundle suite, @NotNull final CoverageDataManager dataManager) {
    final Runnable request = createRenewRequest(suite, dataManager);
    if (request != null) {
      if (myProject.isDisposed()) return;
      final Project project = myProject;
      ProgressManager.getInstance().run(new Task.Backgroundable(project, CoverageBundle.message("coverage.view.loading.data"), true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
            request.run();
        }

        @Override
        public void onSuccess() {
          if (project.isDisposed()) return;
          dataManager.coverageDataCalculated(suite);
        }

        @Override
        public void onCancel() {
          super.onCancel();
          if (project.isDisposed()) return;
          dataManager.closeSuitesBundle(suite);
        }
      });
    }
  }

  public Project getProject() {
    return myProject;
  }

  public boolean hasVcsFilteredChildren() {
    return myHasVcsFilteredChildren;
  }

  public void setVcsFilteredChildren(boolean hasVcsFilteredChildren) {
    myHasVcsFilteredChildren = hasVcsFilteredChildren;
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
