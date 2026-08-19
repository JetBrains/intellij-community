// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.util.ClassUtil;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.rt.coverage.instrumentation.UnloadedUtil;
import jetbrains.coverage.report.ClassInfo;
import jetbrains.coverage.report.ReportBuilderFactory;
import jetbrains.coverage.report.SourceCodeProvider;
import jetbrains.coverage.report.html.HTMLReportBuilder;
import jetbrains.coverage.report.idea.IDEACoverageData;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * @author Roman.Chernyatchik
 */
public abstract class JavaCoverageRunner extends CoverageRunner {
  public static final Class<JaCoCoCoverageRunner> DEFAULT_RUNNER_CLASS = JaCoCoCoverageRunner.class;

  private static final String JAVA_COVERAGE_AGENT_AGENT_PATH = "java.test.agent.lib.path";

  @Override
  public boolean acceptsCoverageEngine(@NotNull CoverageEngine engine) {
    return engine instanceof JavaCoverageEngine;
  }

  public abstract void appendCoverageArgument(final String sessionDataFilePath,
                                              final String @Nullable [] patterns,
                                              final SimpleJavaParameters parameters,
                                              final boolean testTracking,
                                              final boolean branchCoverage);

  public void appendCoverageArgument(final String sessionDataFilePath,
                                     final String @Nullable [] patterns,
                                     String[] excludePatterns,
                                     final SimpleJavaParameters parameters,
                                     final boolean testTracking,
                                     final boolean branchCoverage,
                                     String sourceMapPath,
                                     final Project project) {
    appendCoverageArgument(sessionDataFilePath, patterns, parameters, testTracking, branchCoverage);
  }

  public boolean isBranchInfoAvailable(boolean branchCoverage) {
    return branchCoverage;
  }

  @ApiStatus.Internal
  public @NotNull List<Integer> collectSrcLinesForUntouchedFile(@NotNull Path classFile, @NotNull CoverageSuitesBundle suite) {
    return Collections.emptyList();
  }

  @ApiStatus.Internal
  public @Nullable ClassData getOrLoadCoverage(
    Project project,
    ProjectData projectData,
    @NotNull Supplier<? extends @NotNull ProjectData> unloadedClassesProjectData,
    @NotNull String className,
    boolean isBranchCoverage,
    @NotNull Supplier<byte[]> classBytes
  ) {
    return projectData.getClassData(className);
  }

  public void generateReport(CoverageSuitesBundle suite, Project project) throws IOException {
    final long startNs = System.nanoTime();
    final ProjectData projectData = suite.getCoverageData();
    if (projectData == null) return;
    IDEACoverageRunner.setExcludeAnnotations(project, projectData);
    UnloadedUtil.appendUnloaded(projectData, new IdeaClassFinder(project, suite), suite.isBranchCoverage());

    final long generationStartNs = System.nanoTime();
    final ExportToHTMLSettings settings = ExportToHTMLSettings.getInstance(project);
    final HTMLReportBuilder builder = ReportBuilderFactory.createHTMLReportBuilder();
    builder.setReportDir(new File(settings.OUTPUT_DIRECTORY));
    final SourceCodeProvider sourceCodeProvider = classname -> DumbService.getInstance(project).runReadActionInSmartMode(() -> {
      if (project.isDisposed()) return "";
      final PsiClass psiClass = ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(project), classname);
      return psiClass != null ? psiClass.getNavigationElement().getContainingFile().getText() : "";
    });
    builder.generateReport(new IDEACoverageData(projectData, sourceCodeProvider) {
      @Override
      public @NotNull Collection<ClassInfo> getClasses() {
        final Collection<ClassInfo> classes = super.getClasses();
        JavaCoverageSuite javaCoverageSuite = (JavaCoverageSuite)suite.getSuites()[0];
        if (!suite.isTrackTestFolders() ||
            javaCoverageSuite.getExcludedClassNames().length > 0 ||
            javaCoverageSuite.getExcludedPackageNames().length > 0) {
          final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
          final GlobalSearchScope productionScope = !suite.isTrackTestFolders() ? GlobalSearchScopesCore.projectProductionScope(project)
                                                                                : GlobalSearchScope.projectScope(project);
          for (Iterator<ClassInfo> iterator = classes.iterator(); iterator.hasNext(); ) {
            final ClassInfo aClass = iterator.next();
            final PsiClass psiClass = DumbService.getInstance(project).runReadActionInSmartMode(() -> {
              if (project.isDisposed()) return null;
              return psiFacade.findClass(aClass.getFQName(), productionScope);
            });
            if (psiClass == null ||
                !suite.getCoverageEngine().acceptedByFilters(ReadAction.computeBlocking(() -> psiClass.getContainingFile()), suite)) {
              iterator.remove();
            }
          }
        }
        return classes;
      }
    });
    final long endNs = System.nanoTime();
    final long timeMs = TimeUnit.NANOSECONDS.toMillis(endNs - startNs);
    final long generationTimeMs = TimeUnit.NANOSECONDS.toMillis(endNs - generationStartNs);
    CoverageLogger.logHTMLReport(project, timeMs, generationTimeMs);
  }

  public String generateBriefReport(@NotNull Editor editor,
                                    @NotNull PsiFile psiFile,
                                    @NotNull TextRange range,
                                    @NotNull LineData lineData) {
    return JavaCoverageEngine.createDefaultBriefReport(lineData);
  }

  public static @Nullable String handleSpacesInAgentPath(@NotNull String agentPath) {
    return JavaExecutionUtil.handleSpacesInAgentPath(agentPath, "testAgent", JAVA_COVERAGE_AGENT_AGENT_PATH);
  }

  /**
   * @deprecated Use {@link #write2file(Path, String)} instead.
   */
  @SuppressWarnings("IO_FILE_USAGE")
  @Deprecated
  protected static void write2file(File tempFile, @NonNls String arg) throws IOException {
    write2file(tempFile.toPath(), arg);
  }

  protected static void write2file(Path path, @NonNls String arg) throws IOException {
    Files.writeString(path, arg + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
  }

  /**
   * @deprecated Use {@link #createTempFilePath()} instead.
   */
  @SuppressWarnings("IO_FILE_USAGE")
  @Deprecated
  protected static @NotNull File createTempFile() throws IOException {
    return createTempFilePath().toFile();
  }

  protected static @NotNull Path createTempFilePath() throws IOException {
    Path tempFile = Files.createTempFile("coverage", "args");
    String tempFilePath = tempFile.toAbsolutePath().toString();
    if (tempFilePath.contains(" ")) {
      String path = JavaExecutionUtil.handleSpacesInAgentPath(tempFilePath, "coverage", JAVA_COVERAGE_AGENT_AGENT_PATH);
      if (path == null) throw new IOException("Cannot create temporary file without spaces in path.");
      tempFile = Path.of(path);
    }
    return tempFile;
  }
}
