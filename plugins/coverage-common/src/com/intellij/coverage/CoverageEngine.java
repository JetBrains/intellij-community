package com.intellij.coverage;

import com.intellij.codeInspection.export.ExportToHTMLDialog;
import com.intellij.coverage.view.CoverageViewExtension;
import com.intellij.coverage.view.CoverageViewManager;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * @author Roman.Chernyatchik
 *         <p/>
 *         Coverage engine provide coverage support for different languages or coverage runner classes.
 *         E.g. engine for JVM languages, Ruby, Python
 *         <p/>
 *         Each coverage engine may work with several coverage runner. E.g. Java coverage engine supports IDEA/EMMA/Cobertura,
 *         Ruby engine works with RCov
 */
public abstract class CoverageEngine {
  public static final ExtensionPointName<CoverageEngine> EP_NAME = ExtensionPointName.create("com.intellij.coverageEngine");

  /**
   * Checks whether coverage feature is supported by this engine for given configuration or not.
   *
   * @param conf Run Configuration
   * @return True if coverage for given run configuration is supported by this engine
   */
  public abstract boolean isApplicableTo(@Nullable final RunConfigurationBase conf);

  public abstract boolean canHavePerTestCoverage(@Nullable final RunConfigurationBase conf);

  /**
   * Creates coverage enabled configuration for given RunConfiguration. It is supposed that one run configuration may be associated
   * not more than one coverage engine.
   *
   * @param conf Run Configuration
   * @return Coverage enabled configuration with engine specific settings
   */
  @NotNull
  public abstract CoverageEnabledConfiguration createCoverageEnabledConfiguration(@Nullable final RunConfigurationBase conf);

  /**
   * Coverage suite is coverage settings & coverage data gather by coverage runner (for suites provided by TeamCity server)
   *
   * @param covRunner                Coverage Runner
   * @param name                     Suite name
   * @param coverageDataFileProvider Coverage raw data file provider
   * @param filters                  Coverage data filters
   * @param lastCoverageTimeStamp    timestamp
   * @param suiteToMerge             Suite to merge this coverage data with
   * @param coverageByTestEnabled    Collect coverage for test option
   * @param tracingEnabled           Tracing option
   * @param trackTestFolders         Track test folders option
   * @return Suite
   */
  @Nullable
  public CoverageSuite createCoverageSuite(@NotNull final CoverageRunner covRunner,
                                           @NotNull final String name,
                                           @NotNull final CoverageFileProvider coverageDataFileProvider,
                                           @Nullable final String[] filters,
                                           final long lastCoverageTimeStamp,
                                           @Nullable final String suiteToMerge,
                                           final boolean coverageByTestEnabled,
                                           final boolean tracingEnabled,
                                           final boolean trackTestFolders) {
    return createCoverageSuite(covRunner, name, coverageDataFileProvider, filters, lastCoverageTimeStamp, suiteToMerge,
                               coverageByTestEnabled, tracingEnabled, trackTestFolders, null);
  }

  /**
   * Coverage suite is coverage settings & coverage data gather by coverage runner (for suites provided by TeamCity server)
   *
   *
   * @param covRunner                Coverage Runner
   * @param name                     Suite name
   * @param coverageDataFileProvider Coverage raw data file provider
   * @param filters                  Coverage data filters
   * @param lastCoverageTimeStamp    timestamp
   * @param suiteToMerge             Suite to merge this coverage data with
   * @param coverageByTestEnabled    Collect coverage for test option
   * @param tracingEnabled           Tracing option
   * @param trackTestFolders         Track test folders option
   * @param project
   * @return Suite
   */
  @Nullable
  public abstract CoverageSuite createCoverageSuite(@NotNull final CoverageRunner covRunner,
                                                    @NotNull final String name,
                                                    @NotNull final CoverageFileProvider coverageDataFileProvider,
                                                    @Nullable final String[] filters,
                                                    final long lastCoverageTimeStamp,
                                                    @Nullable final String suiteToMerge,
                                                    final boolean coverageByTestEnabled,
                                                    final boolean tracingEnabled,
                                                    final boolean trackTestFolders, Project project);

  /**
   * Coverage suite is coverage settings & coverage data gather by coverage runner
   *
   * @param covRunner                Coverage Runner
   * @param name                     Suite name
   * @param coverageDataFileProvider
   * @param config                   Coverage engine configuration
   * @return Suite
   */
  @Nullable
  public abstract CoverageSuite createCoverageSuite(@NotNull final CoverageRunner covRunner,
                                                    @NotNull final String name,
                                                    @NotNull final CoverageFileProvider coverageDataFileProvider,
                                                    @NotNull final CoverageEnabledConfiguration config);

  @Nullable
  public abstract CoverageSuite createEmptyCoverageSuite(@NotNull final CoverageRunner coverageRunner);

  /**
   * Coverage annotator which annotates smth(e.g. Project view nodes / editor) with coverage information
   *
   * @param project Project
   * @return Annotator
   */
  @NotNull
  public abstract CoverageAnnotator getCoverageAnnotator(Project project);

  /**
   * Determines if coverage information should be displayed for given file. E.g. coverage may be applicable
   * only to user source files or only for files of specific types
   *
   * @param psiFile file
   * @return false if coverage N/A for given file
   */
  public abstract boolean coverageEditorHighlightingApplicableTo(@NotNull final PsiFile psiFile);

  /**
   * Checks whether file is accepted by coverage filters or not. Is used in Project View Nodes annotator.
   *
   * @param psiFile Psi file
   * @param suite   Coverage suite
   * @return true if included in coverage
   */
  public abstract boolean acceptedByFilters(@NotNull final PsiFile psiFile, @NotNull final CoverageSuitesBundle suite);

  /**
   * E.g. all *.class files for java source file with several classes
   *
   *
   * @param srcFile
   * @param module
   * @return files
   */
  @NotNull
  public Set<File> getCorrespondingOutputFiles(@NotNull final PsiFile srcFile,
                                                        @Nullable final Module module,
                                                        @NotNull final CoverageSuitesBundle suite) {
    final VirtualFile virtualFile = srcFile.getVirtualFile();
    return virtualFile == null ? Collections.emptySet() : Collections.singleton(VfsUtilCore.virtualToIoFile(virtualFile));
  }

  /**
   * When output directory is empty we probably should recompile source and then choose suite again
   *
   * @param module
   * @param chooseSuiteAction @return True if should stop and wait compilation (e.g. for Java). False if we can ignore output (e.g. for Ruby)
   */
  public abstract boolean recompileProjectAndRerunAction(@NotNull final Module module, @NotNull final CoverageSuitesBundle suite,
                                                         @NotNull final Runnable chooseSuiteAction);

  /**
   * Qualified name same as in coverage raw project data
   * E.g. java class qualified name by *.class file of some Java class in corresponding source file
   *
   * @param outputFile
   * @param sourceFile
   * @return
   */
  @Nullable
  public String getQualifiedName(@NotNull final File outputFile,
                                 @NotNull final PsiFile sourceFile) {
    final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(outputFile);
    if (virtualFile != null) {
      return getQualifiedName(virtualFile, sourceFile);
    }
    return null;
  }

  @Deprecated
  @Nullable
  public String getQualifiedName(@NotNull final VirtualFile outputFile,
                                 @NotNull final PsiFile sourceFile) {
    return null;
  }

  /**
   * Returns the list of qualified names of classes generated from a particular source file.
   * (The concept of "qualified name" is specific to each coverage engine but it should be
   * a valid parameter for {@link com.intellij.rt.coverage.data.ProjectData#getClassData(String)}).
   * @param sourceFile
   * @return
   */
  @NotNull
  public abstract Set<String> getQualifiedNames(@NotNull final PsiFile sourceFile);

  /**
   * Decide include a file or not in coverage report if coverage data isn't available for the file. E.g file wasn't touched by coverage
   * util
   *
   * @param qualifiedName
   * @param outputFile
   * @param sourceFile
   * @param suite
   * @return
   */
  public boolean includeUntouchedFileInCoverage(@NotNull final String qualifiedName,
                                                @NotNull final File outputFile,
                                                @NotNull final PsiFile sourceFile,
                                                @NotNull final CoverageSuitesBundle suite) {
    final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(outputFile);
    if (virtualFile != null) {
      return includeUntouchedFileInCoverage(qualifiedName, virtualFile, sourceFile, suite);
    }
    return false;
  }
  
  @Deprecated
  public boolean includeUntouchedFileInCoverage(@NotNull final String qualifiedName,
                                                @NotNull final VirtualFile outputFile,
                                                @NotNull final PsiFile sourceFile,
                                                @NotNull final CoverageSuitesBundle suite) {
    return false;
  }

  /**
   * Collect code lines if untouched file should be included in coverage information. These lines will be marked as uncovered.
   *
   * @param suite
   * @return List (probably empty) of code lines or null if all lines should be marked as uncovered
   */
  @Nullable
  public List<Integer> collectSrcLinesForUntouchedFile(@NotNull final File classFile,
                                                       @NotNull final CoverageSuitesBundle suite) {
    final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(classFile);
    if (virtualFile != null) {
      return collectSrcLinesForUntouchedFile(virtualFile, suite);
    }
    return null;
  }
  
  @Deprecated
  @Nullable
  public List<Integer> collectSrcLinesForUntouchedFile(@NotNull final VirtualFile classFile,
                                                       @NotNull final CoverageSuitesBundle suite) {
    return null;
  }

  /**
   * Content of brief report which will be shown by click on coverage icon
   *
   * @param editor the editor in which the gutter is displayed.
   * @param psiFile the file shown in the editor.
   * @param lineNumber the line number which was clicked.
   * @param startOffset the start offset of that line in the PSI file.
   * @param endOffset the end offset of that line in the PSI file.
   * @param lineData the coverage data for the line.
   * @return the text to show.
   */
  public String generateBriefReport(@NotNull Editor editor,
                                    @NotNull PsiFile psiFile,
                                    int lineNumber,
                                    int startOffset,
                                    int endOffset,
                                    @Nullable LineData lineData) {
    final int hits = lineData == null ? 0 : lineData.getHits();
    return "Hits: " + hits;
  }

  public abstract List<PsiElement> findTestsByNames(@NotNull final String[] testNames, @NotNull final Project project);

  /**
   * To support per test coverage. Return file name which contain traces for given test 
   */
  @Nullable
  public abstract String getTestMethodName(@NotNull final PsiElement element, @NotNull final AbstractTestProxy testProxy);

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

  @NotNull
  public ExportToHTMLDialog createGenerateReportDialog(@NotNull final Project project,
                                                       @NotNull final DataContext dataContext,
                                                       @NotNull final CoverageSuitesBundle currentSuite) {
    final ExportToHTMLDialog dialog = new ExportToHTMLDialog(project, true);
    dialog.setTitle("Generate Coverage Report for: \'" + currentSuite.getPresentableName() + "\'");

    return dialog;
  }

  public abstract String getPresentableText();

  public boolean coverageProjectViewStatisticsApplicableTo(VirtualFile fileOrDir) {
    return false;
  }

  @NotNull
  public Object[] postProcessExecutableLines(@NotNull Object[] lines, Editor editor) {
    return lines;
  }

  public CoverageLineMarkerRenderer getLineMarkerRenderer(int lineNumber,
                                                          @Nullable final String className,
                                                          final TreeMap<Integer, LineData> lines,
                                                          final boolean coverageByTestApplicable,
                                                          @NotNull final CoverageSuitesBundle coverageSuite,
                                                          final Function<Integer, Integer> newToOldConverter,
                                                          final Function<Integer, Integer> oldToNewConverter, boolean subCoverageActive) {
    return CoverageLineMarkerRenderer
      .getRenderer(lineNumber, className, lines, coverageByTestApplicable, coverageSuite, newToOldConverter, oldToNewConverter,
                   subCoverageActive);
  }

  public boolean shouldHighlightFullLines() {
    return false;
  }

  /**
   * 
   * @return true if highlighting should skip the line as it represents no actual source code
   */
  public boolean isGeneratedCode(Project project, String qualifiedName, Object lineData) {
    return false;
  }

  public static String getEditorTitle() {
    return "Code Coverage";
  }

  public CoverageViewExtension createCoverageViewExtension(Project project,
                                                           CoverageSuitesBundle suiteBundle,
                                                           CoverageViewManager.StateBean stateBean) {
    return null;
  }

  public boolean isInLibraryClasses(Project project, VirtualFile file) {
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();

    return projectFileIndex.isInLibraryClasses(file) && !projectFileIndex.isInSource(file);
  }
}
