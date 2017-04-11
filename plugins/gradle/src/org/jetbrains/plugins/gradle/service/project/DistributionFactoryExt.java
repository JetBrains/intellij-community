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

import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.tooling.BuildCancelledException;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.internal.consumer.Distribution;
import org.gradle.tooling.internal.consumer.DistributionFactory;
import org.gradle.tooling.internal.consumer.DistributionInstaller;
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener;
import org.gradle.util.DistributionLocator;
import org.gradle.util.GradleVersion;
import org.gradle.wrapper.*;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.*;

import static org.gradle.internal.FileUtils.hasExtension;

/**
 * @author Vladislav.Soroka
 * @since 8/23/13
 */
public class DistributionFactoryExt extends DistributionFactory {
  /**
   * Returns the default distribution to use for the specified project.
   */
  public Distribution getWrappedDistribution(File propertiesFile) {
    //noinspection UseOfSystemOutOrSystemErr
    WrapperExecutor wrapper = WrapperExecutor.forWrapperPropertiesFile(propertiesFile);
    if (wrapper.getDistribution() != null) {
      return new ZippedDistribution(wrapper.getConfiguration());
    }
    return getDownloadedDistribution(GradleVersion.current().getVersion());
  }

  private Distribution getDownloadedDistribution(String gradleVersion) {
    URI distUri = new DistributionLocator().getDistributionFor(GradleVersion.version(gradleVersion));
    return getDistribution(distUri);
  }

  private static class InstalledDistribution implements Distribution {
    private final File gradleHomeDir;
    private final String displayName;
    private final String locationDisplayName;

    InstalledDistribution(File gradleHomeDir, String displayName, String locationDisplayName) {
      this.gradleHomeDir = gradleHomeDir;
      this.displayName = displayName;
      this.locationDisplayName = locationDisplayName;
    }

    public String getDisplayName() {
      return displayName;
    }

    public ClassPath getToolingImplementationClasspath(ProgressLoggerFactory progressLoggerFactory, InternalBuildProgressListener progressListener, File userHomeDir, BuildCancellationToken cancellationToken) {
      if (!gradleHomeDir.exists()) {
        throw new IllegalArgumentException(String.format("The specified %s does not exist.", locationDisplayName));
      }
      if (!gradleHomeDir.isDirectory()) {
        throw new IllegalArgumentException(String.format("The specified %s is not a directory.", locationDisplayName));
      }
      File libDir = new File(gradleHomeDir, "lib");
      if (!libDir.isDirectory()) {
        throw new IllegalArgumentException(String.format("The specified %s does not appear to contain a Gradle distribution.", locationDisplayName));
      }
      File[] files = libDir.listFiles(new FileFilter() {
        @Override
        public boolean accept(File file) {
          return hasExtension(file, ".jar");
        }
      });
      // Make sure file order is always consistent
      Arrays.sort(files);
      return new DefaultClassPath(files);
    }
  }

  private static class ZippedDistribution implements Distribution {
    private InstalledDistribution installedDistribution;
    private final WrapperConfiguration wrapperConfiguration;

    private ZippedDistribution(WrapperConfiguration wrapperConfiguration) {
      this.wrapperConfiguration = wrapperConfiguration;
    }

    public String getDisplayName() {
      return "Gradle distribution '" + wrapperConfiguration.getDistribution() + "'";
    }

    public ClassPath getToolingImplementationClasspath(ProgressLoggerFactory progressLoggerFactory, final InternalBuildProgressListener progressListener, final File userHomeDir, BuildCancellationToken cancellationToken) {
      if (installedDistribution == null) {
        final DistributionInstaller installer = new DistributionInstaller(progressLoggerFactory, progressListener);
        File installDir;
        try {
          cancellationToken.addCallback(new Runnable() {
            public void run() {
              installer.cancel();
            }
          });
          File realUserHomeDir = userHomeDir != null ? userHomeDir : GradleUserHomeLookup.gradleUserHome();
          installDir = installer.install(realUserHomeDir, wrapperConfiguration);
        } catch (CancellationException e) {
          throw new BuildCancelledException(String.format("Distribution download cancelled. Using distribution from '%s'.", wrapperConfiguration.getDistribution()), e);
        } catch (FileNotFoundException e) {
          throw new IllegalArgumentException(String.format("The specified %s does not exist.", getDisplayName()), e);
        } catch (Exception e) {
          throw new GradleConnectionException(String.format("Could not install Gradle distribution from '%s'.", wrapperConfiguration.getDistribution()), e);
        }
        installedDistribution = new InstalledDistribution(installDir, getDisplayName(), getDisplayName());
      }
      return installedDistribution.getToolingImplementationClasspath(progressLoggerFactory, progressListener, userHomeDir, cancellationToken);
    }
  }
}
