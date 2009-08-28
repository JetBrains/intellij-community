/*
 * User: anna
 * Date: 13-Feb-2008
 */
package com.intellij.coverage;

import com.intellij.coverage.info.CoberturaLoaderUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.process.DefaultJavaProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.rt.coverage.data.ProjectData;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.File;

public class CoberturaCoverageRunner extends CoverageRunner {

  public ProjectData loadCoverageData(final File sessionDataFile) {
    return CoberturaLoaderUtil.load(sessionDataFile);
  }

  public void appendCoverageArgument(final String sessionDataFilePath, final String[] patterns, final JavaParameters javaParameters,
                                     final boolean collectLineInfo, final boolean isSampling) {
    @NonNls StringBuffer argument = new StringBuffer("-javaagent:");
    argument.append(PathManager.getLibPath()).append(File.separator);
    argument.append("cobertura.jar=");

    if (patterns != null && patterns.length > 0) {
      for (String coveragePattern : patterns) {
        coveragePattern = coveragePattern.replace("$", "\\$").replace(".", "\\.").replaceAll("\\*", ".*");
        if (!coveragePattern.endsWith(".*")) { //include inner classes
          coveragePattern += "(\\$.*)*";
        }
        argument.append("--includeClasses ").append(coveragePattern).append(" ");
      }
    }
    if (SystemInfo.isWindows) {
      argument.append("--datafile ").append("\\\"").append(sessionDataFilePath).append("\\\"");
    }
    else {
      argument.append("--datafile ").append(sessionDataFilePath);
    }
    javaParameters.getVMParametersList().add(argument.toString());
    javaParameters.getVMParametersList().defineProperty("net.sourceforge.cobertura.datafile", sessionDataFilePath);
    javaParameters.getClassPath().add(PathManager.getLibPath() + File.separator + "cobertura.jar");
  }

  public void generateReport(final Project project, final String binaryFilePath, final String outputDirectory, final boolean openInBrowser) {
    try {
      final JavaParameters javaParameters = new JavaParameters();
      javaParameters.setMainClass("net.sourceforge.cobertura.reporting.Main");
      javaParameters.getProgramParametersList().add("--destination", outputDirectory);
      javaParameters.getProgramParametersList().add("--datafile", binaryFilePath);
      javaParameters.getClassPath().add(PathManager.getLibPath() + File.separator + "cobertura.jar");
      javaParameters.getClassPath().add(PathManager.getLibPath() + File.separator + "log4j.jar");
      final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(project);
      final VirtualFile[] sourceRoots = projectRootManager.getContentSourceRoots();
      for (VirtualFile sourceRoot : sourceRoots) {
        javaParameters.getProgramParametersList().add(FileUtil.toSystemDependentName(sourceRoot.getPath()));
      }
      javaParameters.setJdk(projectRootManager.getProjectJdk());
      final DefaultJavaProcessHandler processHandler = new DefaultJavaProcessHandler(javaParameters);
      processHandler.startNotify();
      processHandler.addProcessListener(new ProcessAdapter() {
        public void processTerminated(final ProcessEvent event) {
          processHandler.removeProcessListener(this);
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              if (openInBrowser) {
                BrowserUtil.launchBrowser(outputDirectory + File.separator + "index.html");
              }
            }
          });
        }
      });
    }
    catch (ExecutionException e1) {
      //should not happen
    }
  }

  @Override
  public boolean isHTMLReportSupported() {
    return true;
  }

  public String getPresentableName() {
    return "Cobertura";
  }

  @NonNls
  public String getId() {
    return "cobertura";
  }

  @NonNls
  public String getDataFileExtension() {
    return "ser";
  }
}