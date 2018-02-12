/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.coverage;

import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

/**
 * @author Roman.Chernyatchik
 */
public abstract class JavaCoverageRunner extends CoverageRunner {
  private static final Logger LOG = Logger.getInstance(JavaCoverageRunner.class);
  private static final String COVERAGE_AGENT_PATH = "coverage.lib.path";

  public boolean isJdk7Compatible() {
    return true;
  }
  
  @Override
  public boolean acceptsCoverageEngine(@NotNull CoverageEngine engine) {
    return engine instanceof JavaCoverageEngine;
  }

  public abstract void appendCoverageArgument(final String sessionDataFilePath, @Nullable final String[] patterns, final SimpleJavaParameters parameters,
                                              final boolean collectLineInfo, final boolean isSampling);

  public void appendCoverageArgument(final String sessionDataFilePath,
                                     @Nullable final String[] patterns,
                                     String[] excludePatterns,
                                     final SimpleJavaParameters parameters,
                                     final boolean collectLineInfo,
                                     final boolean isSampling,
                                     String sourceMapPath) {
    appendCoverageArgument(sessionDataFilePath, patterns, parameters, collectLineInfo, isSampling);
  }

  protected static String handleSpacesInPath(String agentPath) {
    return handleSpacesInPath(agentPath, null);
  }

  protected static String handleSpacesInPath(String agentPath, FileFilter filter) {
    final String userDefined = System.getProperty(COVERAGE_AGENT_PATH);
    if (userDefined != null && new File(userDefined).exists()) {
      agentPath = userDefined;
    } else {
      agentPath = new File(agentPath).getParent();
    }
    if (!SystemInfo.isWindows && agentPath.contains(" ")) {
      File dir = new File(PathManager.getSystemPath(), "coverageJars");
      if (dir.getAbsolutePath().contains(" ")) {
        try {
          dir = FileUtil.createTempDirectory("coverage", "jars");
          if (dir.getAbsolutePath().contains(" ")) {
            LOG.info("Coverage agent not used since the agent path contains spaces: " + agentPath + "\n" +
                     "One can move the agent libraries to a directory with no spaces in path and specify its path in idea.properties as " + COVERAGE_AGENT_PATH + "=<path>");
            return agentPath;
          }
        }
        catch (IOException e) {
          LOG.info(e);
          return agentPath;
        }
      }

      try {
        LOG.info("Coverage jars were copied to " + dir.getPath());
        FileUtil.copyDir(new File(agentPath), dir, filter);
        return dir.getPath();
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }
    return agentPath;
  }

   protected static void write2file(File tempFile, String arg) throws IOException {
    FileUtil.writeToFile(tempFile, (arg + "\n").getBytes(CharsetToolkit.UTF8_CHARSET), true);
  }

  protected static File createTempFile() throws IOException {
    File tempFile = FileUtil.createTempFile("coverage", "args");
    if (!SystemInfo.isWindows && tempFile.getAbsolutePath().contains(" ")) {
      tempFile = FileUtil.createTempFile(new File(PathManager.getSystemPath(), "coverage"), "coverage", "args", true);
      if (tempFile.getAbsolutePath().contains(" ")) {
        final String userDefined = System.getProperty(COVERAGE_AGENT_PATH);
        if (userDefined != null && new File(userDefined).isDirectory()) {
          tempFile = FileUtil.createTempFile(new File(userDefined), "coverage", "args", true);
        }
      }
    }
    return tempFile;
  }
}
