/*
 * User: anna
 * Date: 20-May-2008
 */
package com.intellij.coverage;

import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.rt.coverage.util.ProjectDataLoader;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class IDEACoverageRunner extends JavaCoverageRunner {
  private static final Logger LOG = Logger.getInstance("#" + IDEACoverageRunner.class.getName());

  public ProjectData loadCoverageData(@NotNull final File sessionDataFile, @Nullable final CoverageSuite coverageSuite) {
    return ProjectDataLoader.load(sessionDataFile);
  }

  public void appendCoverageArgument(final String sessionDataFilePath, final String[] patterns, final SimpleJavaParameters javaParameters,
                                     final boolean collectLineInfo, final boolean isSampling) {
    StringBuilder argument = new StringBuilder("-javaagent:");
    final String agentPath = PathUtil.getJarPathForClass(ProjectData.class);
    final String parentPath = handleSpacesInPath(agentPath);
    argument.append(parentPath).append(File.separator).append(new File(agentPath).getName());
    argument.append("=");
    try {
      final File tempFile = createTempFile();
      tempFile.deleteOnExit();
      write2file(tempFile, sessionDataFilePath);
      write2file(tempFile, String.valueOf(collectLineInfo));
      write2file(tempFile, Boolean.FALSE.toString()); //append unloaded
      write2file(tempFile, Boolean.FALSE.toString());//merge with existing
      write2file(tempFile, String.valueOf(isSampling));
      if (patterns != null) {
        for (String coveragePattern : patterns) {
          coveragePattern = coveragePattern.replace("$", "\\$").replace(".", "\\.").replaceAll("\\*", ".*");
          if (!coveragePattern.endsWith(".*")) { //include inner classes
            coveragePattern += "(\\$.*)*";
          }
          write2file(tempFile, coveragePattern);
        }
      }
      argument.append(tempFile.getCanonicalPath());
    }
    catch (IOException e) {
      LOG.info("Coverage was not enabled", e);
      return;
    }

    javaParameters.getVMParametersList().add(argument.toString());
  }


  public String getPresentableName() {
    return "IntelliJ IDEA";
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