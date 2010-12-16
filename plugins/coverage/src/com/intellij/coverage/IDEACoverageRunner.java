/*
 * User: anna
 * Date: 20-May-2008
 */
package com.intellij.coverage;

import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.rt.coverage.util.ProjectDataLoader;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class IDEACoverageRunner extends JavaCoverageRunner {
  private static final Logger LOG = Logger.getInstance("#" + IDEACoverageRunner.class.getName());

  public ProjectData loadCoverageData(@NotNull final File sessionDataFile, @Nullable final CoverageSuite coverageSuite) {
    return ProjectDataLoader.load(sessionDataFile);
  }

  public void appendCoverageArgument(final String sessionDataFilePath, final String[] patterns, final SimpleJavaParameters javaParameters,
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