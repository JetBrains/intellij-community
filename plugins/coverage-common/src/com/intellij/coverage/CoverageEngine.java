// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.codeInspection.export.ExportToHTMLDialog;
import com.intellij.coverage.view.CoverageViewExtension;
import com.intellij.coverage.view.CoverageViewManager;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsContexts.TabTitle;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.util.Function;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * Coverage engine provides coverage support for different languages or coverage runner classes.
 * E.g., engine for JVM languages, Ruby, Python
 * <p/>
 * Each coverage engine may work with several coverage runners.
 * E.g., Java coverage engine supports IDEA/Jacoco, Ruby engine works with RCov
 *
 * @author Roman.Chernyatchik
 */
public abstract class CoverageEngine {
  public static final ExtensionPointName<CoverageEngine> EP_NAME = ExtensionPointName.create("com.intellij.coverageEngine");

  public abstract @NlsActions.ActionText String getPresentableText();

  /**
   * Checks whether this engine supports coverage for a given configuration or not.
   *
   * @param conf Run Configuration
   * @return True if this engine supports coverage for a given run configuration
   */
  public abstract boolean isApplicableTo(@NotNull final RunConfigurationBase<?> conf);

  /**
   * Creates coverage enabled configuration for given RunConfiguration. It is supposed that one run configuration may be associated
   * with no more than one coverage engine.
   *
   * @param conf Run Configuration
   * @return Coverage enabled configuration with engine-specific settings
   */
  @NotNull
  public abstract CoverageEnabledConfiguration createCoverageEnabledConfiguration(@NotNull final RunConfigurationBase<?> conf);


  /**
   * Create a suite from an external report.
   */
  public final @Nullable CoverageSuite createCoverageSuite(@NotNull String name,
                                                           @NotNull Project project,
                                                           @NotNull CoverageRunner runner,
                                                           @NotNull CoverageFileProvider fileProvider,
                                                           long timestamp) {
    return createCoverageSuite(runner, name, fileProvider, null, timestamp, null, false, false, false, project);
  }


  /**
   * Create a suite from a run configuration.
   */
  public @Nullable CoverageSuite createCoverageSuite(@NotNull CoverageEnabledConfiguration config) {
    CoverageRunner runner = config.getCoverageRunner();
    if (runner == null) return null;
    return createCoverageSuite(runner, config.createSuiteName(), config.createFileProvider(), config);
  }

  /**
   * Coverage suite is coverage settings & coverage data gather by coverage runner. This method is used for external suites.
   *
   * @param runner                Coverage Runner
   * @param name                  Suite name
   * @param fileProvider          Coverage raw data file provider
   * @param filters               Coverage data filters
   * @param lastCoverageTimeStamp timestamp
   * @param suiteToMerge          Suite to merge this coverage data with
   * @param coverageByTestEnabled Collect coverage per test
   * @param branchCoverage        Whether the suite includes branch coverage, or only line coverage otherwise
   * @param trackTestFolders      Track test folders option
   * @return Suite
   * @deprecated Use {@link CoverageEngine#createCoverageSuite(String, Project, CoverageRunner, CoverageFileProvider, long)}
   */
  @Deprecated
  @Nullable
  public abstract CoverageSuite createCoverageSuite(@NotNull CoverageRunner runner,
                                                    @NotNull String name,
                                                    @NotNull CoverageFileProvider fileProvider,
                                                    String @Nullable [] filters,
                                                    long lastCoverageTimeStamp,
                                                    @Nullable String suiteToMerge,
                                                    boolean coverageByTestEnabled,
                                                    boolean branchCoverage,
                                                    boolean trackTestFolders, Project project);

  /**
   * @deprecated Use {@link CoverageEngine#createCoverageSuite(CoverageEnabledConfiguration)}
   */
  @Deprecated
  @Nullable
  public abstract CoverageSuite createCoverageSuite(@NotNull CoverageRunner covRunner,
                                                    @NotNull String name,
                                                    @NotNull CoverageFileProvider coverageDataFileProvider,
                                                    @NotNull CoverageEnabledConfiguration config);

  /**
   * Create a new suite with no parameters set.
   * <p/>
   * This method is used to read a suite from persistent storage.
   */
  @Nullable
  public abstract CoverageSuite createEmptyCoverageSuite(@NotNull CoverageRunner coverageRunner);

  /**
   * Coverage annotator which annotates smth(e.g. Project view nodes / editor) with coverage information
   *
   * @param project Project
   * @return Annotator
   */
  @NotNull
  public abstract CoverageAnnotator getCoverageAnnotator(Project project);

  /**
   * Determines if coverage information should be displayed for a given file.
   * E.g., coverage may be applicable only to user source files or only for files of specific types
   *
   * @param psiFile file
   * @return false if coverage N/A for given file
   */
  public boolean coverageEditorHighlightingApplicableTo(@NotNull final PsiFile psiFile) {
    return true;
  }

  /**
   * Checks whether file is accepted by coverage filters or not. Is used in Project View Nodes annotator.
   *
   * @param psiFile Psi file
   * @param suite   Coverage suite
   * @return true if included in coverage
   */
  public boolean acceptedByFilters(@NotNull final PsiFile psiFile, @NotNull final CoverageSuitesBundle suite) {
    return true;
  }

  /**
   * E.g., all *.class files for java source file with several classes
   *
   * @return files
   */
  @ApiStatus.Internal
  @NotNull
  public Set<File> getCorrespondingOutputFiles(@NotNull final PsiFile srcFile,
                                               @Nullable final Module module,
                                               @NotNull final CoverageSuitesBundle suite) {
    final VirtualFile virtualFile = srcFile.getVirtualFile();
    return virtualFile == null ? Collections.emptySet() : Collections.singleton(VfsUtilCore.virtualToIoFile(virtualFile));
  }

  /**
   * When output directory is empty we probably should recompile the source and then choose a suite again
   *
   * @return True, if should stop and wait for compilation (e.g., for Java). False if we can ignore output (e.g., for Ruby)
   */
  @ApiStatus.Internal
  public boolean recompileProjectAndRerunAction(@NotNull final Module module, @NotNull final CoverageSuitesBundle suite,
                                                @NotNull final Runnable chooseSuiteAction) {
    return false;
  }

  /**
   * Qualified name same as in coverage raw project data
   * E.g., java class qualified name by *.class file of some Java class in corresponding source file
   */
  @ApiStatus.Internal
  @Nullable
  protected String getQualifiedName(@NotNull final File outputFile,
                                 @NotNull final PsiFile sourceFile) {
    return null;
  }

  /**
   * Returns the list of qualified names of classes generated from a particular source file.
   * (The concept of "qualified name" is specific to each coverage engine, but it should be
   * a valid parameter for {@link com.intellij.rt.coverage.data.ProjectData#getClassData(String)}).
   */
  @NotNull
  public Set<String> getQualifiedNames(@NotNull final PsiFile sourceFile) {
    return Collections.emptySet();
  }

  /**
   * Decide to include a file or not in a coverage report if coverage data isn't available for the file.
   * E.g., file wasn't touched by coverage util
   */
  @ApiStatus.Internal
  public boolean includeUntouchedFileInCoverage(@NotNull final String qualifiedName,
                                                @NotNull final File outputFile,
                                                @NotNull final PsiFile sourceFile,
                                                @NotNull final CoverageSuitesBundle suite) {
    return false;
  }

  /**
   * Collect code lines if untouched file should be included in coverage information. These lines will be marked as uncovered.
   *
   * @return List (probably empty) of code lines or null if all lines should be marked as uncovered
   */
  @ApiStatus.Internal
  @Nullable
  public List<Integer> collectSrcLinesForUntouchedFile(@NotNull final File classFile,
                                                       @NotNull final CoverageSuitesBundle suite) {
    return null;
  }

  /**
   * Content of a brief report which will be shown by click on coverage icon
   *
   * @param editor      the editor in which the gutter is displayed.
   * @param psiFile     the file shown in the editor.
   * @param lineNumber  the line number which was clicked.
   * @param startOffset the start offset of that line in the PSI file.
   * @param endOffset   the end offset of that line in the PSI file.
   * @param lineData    the coverage data for the line.
   * @return the text to show.
   */
  @ApiStatus.Internal
  public String generateBriefReport(@NotNull Editor editor,
                                    @NotNull PsiFile psiFile,
                                    int lineNumber,
                                    int startOffset,
                                    int endOffset,
                                    @Nullable LineData lineData) {
    final int hits = lineData == null ? 0 : lineData.getHits();
    return CoverageBundle.message("hits.title", hits);
  }

  /**
   * Content of a brief report which will be shown by click on coverage icon
   */
  @ApiStatus.Internal
  public String generateBriefReport(@NotNull CoverageSuitesBundle bundle,
                                    @NotNull Editor editor,
                                    @NotNull PsiFile psiFile,
                                    @NotNull TextRange range,
                                    @Nullable LineData lineData) {
    int lineNumber = editor.getDocument().getLineNumber(range.getStartOffset());
    return generateBriefReport(editor, psiFile, lineNumber, range.getStartOffset(), range.getEndOffset(), lineData);
  }

  /**
   * @return true to enable 'Generate Coverage Report...' action
   */
  public boolean isReportGenerationAvailable(@NotNull Project project,
                                             @NotNull DataContext dataContext,
                                             @NotNull CoverageSuitesBundle currentSuite) {
    return false;
  }

  public void generateReport(@NotNull final Project project,
                             @NotNull final DataContext dataContext,
                             @NotNull final CoverageSuitesBundle currentSuite) {
  }

  @ApiStatus.Internal
  @NotNull
  public ExportToHTMLDialog createGenerateReportDialog(@NotNull final Project project,
                                                       @NotNull final DataContext dataContext,
                                                       @NotNull final CoverageSuitesBundle currentSuite) {
    final ExportToHTMLDialog dialog = new ExportToHTMLDialog(project, true);
    dialog.setTitle(CoverageBundle.message("generate.coverage.report.for", currentSuite.getPresentableName()));
    final ExportToHTMLSettings settings = ExportToHTMLSettings.getInstance(project);
    if (StringUtil.isEmpty(settings.OUTPUT_DIRECTORY)) {
      final VirtualFile file = ProjectUtil.guessProjectDir(project);
      if (file != null) {
        final String path = file.getCanonicalPath();
        if (path != null) {
          settings.OUTPUT_DIRECTORY = FileUtil.toSystemDependentName(path) + File.separator + "htmlReport";
        }
      }
    }

    return dialog;
  }

  @ApiStatus.Internal
  public boolean coverageProjectViewStatisticsApplicableTo(VirtualFile fileOrDir) {
    return false;
  }

  @ApiStatus.Internal
  protected Object @NotNull [] postProcessExecutableLines(Object @NotNull [] lines, @NotNull Editor editor) {
    return lines;
  }

  @ApiStatus.Internal
  public CoverageLineMarkerRenderer getLineMarkerRenderer(int lineNumber,
                                                          @Nullable final String className,
                                                          @NotNull final TreeMap<Integer, LineData> lines,
                                                          final boolean coverageByTestApplicable,
                                                          @NotNull final CoverageSuitesBundle coverageSuite,
                                                          final Function<? super Integer, Integer> newToOldConverter,
                                                          final Function<? super Integer, Integer> oldToNewConverter,
                                                          boolean subCoverageActive) {
    return CoverageLineMarkerRenderer
      .getRenderer(lineNumber, className, lines, coverageByTestApplicable, coverageSuite, newToOldConverter, oldToNewConverter,
                   subCoverageActive);
  }

  /**
   * @return true if highlighting should skip the line as it represents no actual source code
   */
  @ApiStatus.Internal
  protected boolean isGeneratedCode(Project project, String qualifiedName, Object lineData) {
    return false;
  }

  @ApiStatus.Internal
  @NotNull
  public CoverageEditorAnnotator createSrcFileAnnotator(PsiFile file, Editor editor) {
    return new CoverageEditorAnnotatorImpl(file, editor);
  }

  @ApiStatus.Internal
  public static @TabTitle String getEditorTitle() {
    return CoverageBundle.message("coverage.tab.title");
  }

  /**
   * @deprecated Use {@link #createCoverageViewExtension(Project, CoverageSuitesBundle)}
   */
  @Deprecated
  public CoverageViewExtension createCoverageViewExtension(Project project,
                                                           CoverageSuitesBundle suiteBundle,
                                                           @SuppressWarnings("unused") CoverageViewManager.StateBean stateBean) {
    return createCoverageViewExtension(project, suiteBundle);
  }

  public CoverageViewExtension createCoverageViewExtension(Project project,
                                                           CoverageSuitesBundle suiteBundle) {
    return null;
  }

  public boolean isInLibraryClasses(final Project project, final VirtualFile file) {
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();

    return ReadAction.compute(() -> projectFileIndex.isInLibraryClasses(file) && !projectFileIndex.isInSource(file));
  }

  @ApiStatus.Internal
  protected boolean isInLibrarySource(Project project, VirtualFile file) {
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();

    return ReadAction.compute(() -> projectFileIndex.isInLibrarySource(file));
  }

  @ApiStatus.Internal
  public boolean canHavePerTestCoverage(@NotNull final RunConfigurationBase<?> conf) {
    return false;
  }

  /**
   * @return tests, which covered specified line. Names should be compatible with {@link CoverageEngine#findTestsByNames(String[], Project)}
   */
  @ApiStatus.Internal
  public Set<String> getTestsForLine(Project project, CoverageSuitesBundle bundle, String classFQName, int lineNumber) {
    return Collections.emptySet();
  }

  /**
   * @return true, if test data was collected
   */
  @ApiStatus.Internal
  public boolean wasTestDataCollected(Project project, CoverageSuitesBundle bundle) {
    return false;
  }

  @ApiStatus.Internal
  public List<PsiElement> findTestsByNames(final String @NotNull [] testNames, @NotNull final Project project) {
    return Collections.emptyList();
  }

  /**
   * To support per test coverage. Return file name which contains traces for a given test
   */
  @ApiStatus.Internal
  @Nullable
  public String getTestMethodName(@NotNull final PsiElement element, @NotNull final AbstractTestProxy testProxy) {
    return null;
  }

  /**
   * Extract coverage data by subset of executed tests
   *
   * @param sanitizedTestNames sanitized qualified method names for which traces should be collected
   * @param suite              suite to find corresponding traces
   * @param trace              class - lines map, corresponding to the lines covered by sanitizedTestNames
   */
  @ApiStatus.Internal
  public void collectTestLines(List<String> sanitizedTestNames, CoverageSuite suite, Map<String, Set<Integer>> trace) { }

  @ApiStatus.Internal
  protected void deleteAssociatedTraces(CoverageSuite suite) { }

  /**
   * @deprecated Is not used
   */
  @Deprecated(forRemoval = true)
  @Nullable
  public CoverageSuite createCoverageSuite(@NotNull CoverageRunner covRunner,
                                           @NotNull String name,
                                           @NotNull CoverageFileProvider coverageDataFileProvider,
                                           String @Nullable [] filters,
                                           long lastCoverageTimeStamp,
                                           @Nullable String suiteToMerge,
                                           boolean coverageByTestEnabled,
                                           boolean branchCoverage,
                                           boolean trackTestFolders) {
    return createCoverageSuite(covRunner, name, coverageDataFileProvider, filters, lastCoverageTimeStamp, suiteToMerge,
                               coverageByTestEnabled, branchCoverage, trackTestFolders, null);
  }
}
