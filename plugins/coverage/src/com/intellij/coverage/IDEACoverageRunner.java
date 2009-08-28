/*
 * User: anna
 * Date: 20-May-2008
 */
package com.intellij.coverage;

import com.intellij.execution.configurations.JavaParameters;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.ClassUtil;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.rt.coverage.util.ProjectDataLoader;
import com.intellij.util.PathUtil;
import jetbrains.coverage.report.ReportBuilderFactory;
import jetbrains.coverage.report.SourceCodeProvider;
import jetbrains.coverage.report.html.HTMLReportBuilder;
import jetbrains.coverage.report.idea.IDEACoverageData;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class IDEACoverageRunner extends CoverageRunner {
  private static final Logger LOG = Logger.getInstance("#" + IDEACoverageRunner.class.getName());

  public ProjectData loadCoverageData(final File sessionDataFile) {
    return ProjectDataLoader.load(sessionDataFile);
  }

  public void appendCoverageArgument(final String sessionDataFilePath, final String[] patterns, final JavaParameters javaParameters,
                                     final boolean collectLineInfo, final boolean isSampling) {
    StringBuffer argument = new StringBuffer("-javaagent:");
    argument.append(PathUtil.getJarPathForClass(ProjectData.class));
    argument.append("=");
    if (SystemInfo.isWindows) {
      argument.append("\\\"").append(sessionDataFilePath).append("\\\"");
    }
    else {
      argument.append(sessionDataFilePath);
    }
    argument.append(" ").append(String.valueOf(collectLineInfo));
    argument.append(" ").append(Boolean.FALSE.toString()); //append unloaded
    argument.append(" ").append(Boolean.FALSE.toString()); //merge with existing
    argument.append(" ").append(String.valueOf(isSampling));
    if (patterns != null && patterns.length > 0) {
      argument.append(" ");
      for (String coveragePattern : patterns) {
        coveragePattern = coveragePattern.replace("$", "\\$").replace(".", "\\.").replaceAll("\\*", ".*");
        if (!coveragePattern.endsWith(".*")) { //include inner classes
          coveragePattern += "(\\$.*)*";
        }
        argument.append(coveragePattern).append(" ");
      }
    }
    javaParameters.getVMParametersList().add(argument.toString());
    javaParameters.getVMParametersList().add("-Xbootclasspath/p:" +  PathManager.getLibPath() + File.separator + "coverage-agent.jar");
    javaParameters.getClassPath().add(PathManager.getLibPath() + File.separator + "coverage-agent.jar");
  }

  @Override
  public boolean isHTMLReportSupported() {
    return true;
  }

  @Override
  public void generateReport(final Project project, final String coverageDataFileName, final String outputDir, final boolean openInBrowser) {
    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Generating coverage report ...") {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        try {
          final HTMLReportBuilder builder = ReportBuilderFactory.createHTMLReportBuilder();
          builder.setReportDir(new File(outputDir));
          builder.generateReport(new IDEACoverageData(coverageDataFileName, new SourceCodeProvider() {
            public String getSourceCode(@NotNull final String classname) throws IOException {
              return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
                public String compute() {
                  final PsiClass psiClass = ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(project), classname);
                  return psiClass != null ? psiClass.getContainingFile().getText() : "";
                }
              });
            }
          }));

        }
        catch (IOException e) {
          LOG.error(e);
        }
      }

      @Override
      public void onSuccess() {
        if (openInBrowser) BrowserUtil.launchBrowser(VfsUtil.pathToUrl(outputDir + "/index.html"));
      }
    });
  }

  public String getPresentableName() {
    return "IDEA";
  }

  public String getId() {
    return "idea";
  }

  public String getDataFileExtension() {
    return "ic";
  }

  @Override
  public boolean isCoverageByTestApplicable() {
    return true;
  }
}