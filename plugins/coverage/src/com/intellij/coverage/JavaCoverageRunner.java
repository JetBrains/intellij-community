// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.util.ClassUtil;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.rt.coverage.instrumentation.SaveHook;
import jetbrains.coverage.report.ClassInfo;
import jetbrains.coverage.report.ReportBuilderFactory;
import jetbrains.coverage.report.SourceCodeProvider;
import jetbrains.coverage.report.html.HTMLReportBuilder;
import jetbrains.coverage.report.idea.IDEACoverageData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Iterator;

/**
 * @author Roman.Chernyatchik
 */
public abstract class JavaCoverageRunner extends CoverageRunner {
  private static final String JAVA_COVERAGE_AGENT_AGENT_PATH = "java.test.agent.lib.path";

  public boolean isJdk7Compatible() {
    return true;
  }

  @Override
  public boolean acceptsCoverageEngine(@NotNull CoverageEngine engine) {
    return engine instanceof JavaCoverageEngine;
  }

  public abstract void appendCoverageArgument(final String sessionDataFilePath, final String @Nullable [] patterns, final SimpleJavaParameters parameters,
                                              final boolean collectLineInfo, final boolean isSampling);

  public void appendCoverageArgument(final String sessionDataFilePath,
                                     final String @Nullable [] patterns,
                                     String[] excludePatterns,
                                     final SimpleJavaParameters parameters,
                                     final boolean collectLineInfo,
                                     final boolean isSampling,
                                     String sourceMapPath) {
    appendCoverageArgument(sessionDataFilePath, patterns, parameters, collectLineInfo, isSampling);
  }

  public boolean isBranchInfoAvailable(boolean sampling) {
    return !sampling;
  }

  public void generateReport(CoverageSuitesBundle suite, Project project) throws IOException {
    final ProjectData projectData = suite.getCoverageData();
    final ExportToHTMLSettings settings = ExportToHTMLSettings.getInstance(project);
    final File tempFile = FileUtil.createTempFile("temp", "");
    tempFile.deleteOnExit();
    new SaveHook(tempFile, true, new IdeaClassFinder(project, suite)).save(projectData);
    final HTMLReportBuilder builder = ReportBuilderFactory.createHTMLReportBuilder();
    builder.setReportDir(new File(settings.OUTPUT_DIRECTORY));
    final SourceCodeProvider sourceCodeProvider = classname -> DumbService.getInstance(project).runReadActionInSmartMode(() -> {
      if (project.isDisposed()) return "";
      final PsiClass psiClass = ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(project), classname);
      return psiClass != null ? psiClass.getNavigationElement().getContainingFile().getText() : "";
    });
    builder.generateReport(new IDEACoverageData(projectData, sourceCodeProvider) {
      @NotNull
      @Override
      public Collection<ClassInfo> getClasses() {
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
            if (psiClass == null || !suite.getCoverageEngine().acceptedByFilters(psiClass.getContainingFile(), suite)) {
              iterator.remove();
            }
          }
        }
        return classes;
      }
    });
  }

  @Nullable
  public static String handleSpacesInAgentPath(@NotNull String agentPath) {
    return JavaExecutionUtil.handleSpacesInAgentPath(agentPath, "testAgent", JAVA_COVERAGE_AGENT_AGENT_PATH);
  }

  protected static void write2file(File tempFile, String arg) throws IOException {
    FileUtil.writeToFile(tempFile, (arg + "\n").getBytes(StandardCharsets.UTF_8), true);
  }

  protected static File createTempFile() throws IOException {
    File tempFile = FileUtil.createTempFile("coverage", "args");
    if (!SystemInfo.isWindows && tempFile.getAbsolutePath().contains(" ")) {
      tempFile = FileUtil.createTempFile(new File(PathManager.getSystemPath(), "coverage"), "coverage", "args", true);
      if (tempFile.getAbsolutePath().contains(" ")) {
        final String userDefined = System.getProperty(JAVA_COVERAGE_AGENT_AGENT_PATH);
        if (userDefined != null && new File(userDefined).isDirectory()) {
          tempFile = FileUtil.createTempFile(new File(userDefined), "coverage", "args", true);
        }
      }
    }
    return tempFile;
  }
}
