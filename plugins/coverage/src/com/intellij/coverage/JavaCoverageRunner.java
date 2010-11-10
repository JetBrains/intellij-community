package com.intellij.coverage;

import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.rt.coverage.instrumentation.SaveHook;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author Roman.Chernyatchik
 */
public abstract class JavaCoverageRunner extends CoverageRunner {
  private static final Logger LOG = Logger.getInstance("#" + JavaCoverageRunner.class.getName());

  @Override
  public boolean acceptsCoverageEngine(@NotNull CoverageEngine engine) {
    return engine instanceof JavaCoverageEngine;
  }

  public abstract void appendCoverageArgument(final String sessionDataFilePath, @Nullable final String[] patterns, final SimpleJavaParameters parameters,
                                              final boolean collectLineInfo, final boolean isSampling);
  protected abstract void generateJavaReport(@NotNull Project project,
                                             boolean trackTestFolders,
                                             @NotNull String coverageDataFileName,
                                             @Nullable String outputDir,
                                             boolean openInBrowser);

  @Override
  public final void generateReport(@NotNull final Project project,
                                   @NotNull final DataContext dataContext,
                                   @NotNull final CoverageSuite currentSuite) {
    final CoverageDataManager coverageDataManager = CoverageDataManager.getInstance(project);

    try {
      final File tempFile = FileUtil.createTempFile("temp", "");
      tempFile.deleteOnExit();
      final ProjectData projectData = currentSuite.getCoverageData(coverageDataManager);
      new SaveHook(tempFile, true, new IdeaClassFinder(project, (JavaCoverageSuite)currentSuite)).save(projectData);
      final ExportToHTMLSettings settings = ExportToHTMLSettings.getInstance(project);
      generateJavaReport(project,
                         currentSuite.isTrackTestFolders(),
                         tempFile.getCanonicalPath(),
                         settings.OUTPUT_DIRECTORY,
                         settings.OPEN_IN_BROWSER);
    }
    catch (IOException e1) {
      LOG.error(e1);
    }
  }
}
