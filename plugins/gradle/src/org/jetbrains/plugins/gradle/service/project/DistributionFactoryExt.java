/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.service.project;

import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.internal.consumer.Distribution;
import org.gradle.tooling.internal.consumer.DistributionFactory;
import org.gradle.util.DistributionLocator;
import org.gradle.util.GradleVersion;
import org.gradle.wrapper.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 * @since 8/23/13
 */
public class DistributionFactoryExt extends DistributionFactory {
  private final File userHomeDir;

  public DistributionFactoryExt(File userHomeDir) {
    super(userHomeDir);
    this.userHomeDir = userHomeDir;
  }

  /**
   * Returns the default distribution to use for the specified project.
   */
  public Distribution getWrappedDistribution(File propertiesFile) {
    //noinspection UseOfSystemOutOrSystemErr
    WrapperExecutor wrapper = WrapperExecutor.forWrapperPropertiesFile(propertiesFile, System.out);
    if (wrapper.getDistribution() != null) {
      return new ZippedDistribution(wrapper.getConfiguration());
    }
    return getDownloadedDistribution(GradleVersion.current().getVersion());
  }

  private Distribution getDownloadedDistribution(String gradleVersion) {
    URI distUri = new DistributionLocator().getDistributionFor(GradleVersion.version(gradleVersion));
    return getDistribution(distUri);
  }

  private static class ProgressReportingDownload implements IDownload {
    private final ProgressLoggerFactory progressLoggerFactory;

    private ProgressReportingDownload(ProgressLoggerFactory progressLoggerFactory) {
      this.progressLoggerFactory = progressLoggerFactory;
    }

    public void download(URI address, File destination) throws Exception {
      ProgressLogger progressLogger = progressLoggerFactory.newOperation(DistributionFactory.class);
      progressLogger.setDescription(String.format("Download %s", address));
      progressLogger.started();
      try {
        new Download("Gradle Tooling API", GradleVersion.current().getVersion()).download(address, destination);
      }
      finally {
        progressLogger.completed();
      }
    }
  }

  private static class InstalledDistribution implements Distribution {
    private final File gradleHomeDir;
    private final String displayName;
    private final String locationDisplayName;

    public InstalledDistribution(File gradleHomeDir, String displayName, String locationDisplayName) {
      this.gradleHomeDir = gradleHomeDir;
      this.displayName = displayName;
      this.locationDisplayName = locationDisplayName;
    }

    public String getDisplayName() {
      return displayName;
    }

    public ClassPath getToolingImplementationClasspath(ProgressLoggerFactory progressLoggerFactory) {
      ProgressLogger progressLogger = progressLoggerFactory.newOperation(DistributionFactory.class);
      progressLogger.setDescription("Validate distribution");
      progressLogger.started();
      try {
        return getToolingImpl();
      }
      finally {
        progressLogger.completed();
      }
    }

    private ClassPath getToolingImpl() {
      if (!gradleHomeDir.exists()) {
        throw new IllegalArgumentException(String.format("The specified %s does not exist.", locationDisplayName));
      }
      if (!gradleHomeDir.isDirectory()) {
        throw new IllegalArgumentException(String.format("The specified %s is not a directory.", locationDisplayName));
      }
      File libDir = new File(gradleHomeDir, "lib");
      if (!libDir.isDirectory()) {
        throw new IllegalArgumentException(
          String.format("The specified %s does not appear to contain a Gradle distribution.", locationDisplayName));
      }
      Set<File> files = new LinkedHashSet<File>();
      //noinspection ConstantConditions
      for (File file : libDir.listFiles()) {
        if (file.getName().endsWith(".jar")) {
          files.add(file);
        }
      }
      return new DefaultClassPath(files);
    }
  }

  private class ZippedDistribution implements Distribution {
    private final WrapperConfiguration wrapperConfiguration;
    private InstalledDistribution installedDistribution;

    private ZippedDistribution(WrapperConfiguration wrapperConfiguration) {
      this.wrapperConfiguration = wrapperConfiguration;
    }

    public String getDisplayName() {
      return String.format("Gradle distribution '%s'", wrapperConfiguration.getDistribution());
    }

    public ClassPath getToolingImplementationClasspath(ProgressLoggerFactory progressLoggerFactory) {
      if (installedDistribution == null) {
        File installDir;
        try {
          Install install = new Install(new ProgressReportingDownload(progressLoggerFactory), new PathAssembler(userHomeDir));
          installDir = install.createDist(wrapperConfiguration);
        }
        catch (FileNotFoundException e) {
          throw new IllegalArgumentException(String.format("The specified %s does not exist.", getDisplayName()), e);
        }
        catch (Exception e) {
          throw new GradleConnectionException(
            String.format("Could not install Gradle distribution from '%s'.", wrapperConfiguration.getDistribution()), e);
        }
        installedDistribution = new InstalledDistribution(installDir, getDisplayName(), getDisplayName());
      }
      return installedDistribution.getToolingImplementationClasspath(progressLoggerFactory);
    }
  }
}
