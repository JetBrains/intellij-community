// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.time.Clock;
import org.gradle.internal.time.Time;
import org.gradle.tooling.BuildCancelledException;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.internal.consumer.ConnectionParameters;
import org.gradle.tooling.internal.consumer.Distribution;
import org.gradle.tooling.internal.consumer.DistributionFactory;
import org.gradle.tooling.internal.consumer.DistributionInstaller;
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener;
import org.gradle.util.GradleVersion;
import org.gradle.util.internal.DistributionLocator;
import org.gradle.wrapper.GradleUserHomeLookup;
import org.gradle.wrapper.PropertiesFileHandler;
import org.gradle.wrapper.WrapperConfiguration;
import org.gradle.wrapper.WrapperExecutor;
import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;

import static org.gradle.internal.FileUtils.hasExtension;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
public final class DistributionFactoryExt extends DistributionFactory {

  private DistributionFactoryExt() {
    super(Time.clock());
  }

  public static void setWrappedDistribution(GradleConnector connector, String wrapperPropertyFile) {
    File propertiesFile = new File(wrapperPropertyFile);
    if (propertiesFile.exists()) {
      WrapperExecutor wrapper = WrapperExecutor.forWrapperPropertiesFile(propertiesFile);
      if (wrapper.getDistribution() != null) {
        Distribution distribution = new DistributionFactoryExt().getWrappedDistribution(propertiesFile);
        try {
          setDistributionField(connector, distribution);
        }
        catch (Exception e) {
          ExternalSystemException externalSystemException = new ExternalSystemException(e);
          externalSystemException.initCause(e);
          throw externalSystemException;
        }
      }
    }
  }

  /**
   * Returns the default distribution to use for the specified project.
   */
  private Distribution getWrappedDistribution(File propertiesFile) {
    WrapperExecutor wrapper = WrapperExecutor.forWrapperPropertiesFile(propertiesFile);
    if (wrapper.getDistribution() != null) {
      return new ZippedDistribution(wrapper.getConfiguration(), Time.clock());
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

    @Override
    public String getDisplayName() {
      return displayName;
    }

    @Override
    public ClassPath getToolingImplementationClasspath(ProgressLoggerFactory progressLoggerFactory,
                                                       InternalBuildProgressListener progressListener,
                                                       ConnectionParameters connectionParameters,
                                                       BuildCancellationToken cancellationToken) {
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
      File[] files = libDir.listFiles(file -> hasExtension(file, ".jar"));
      // Make sure file order is always consistent
      if (files != null) {
        Arrays.sort(files);
      }
      return DefaultClassPath.of(files);
    }
  }

  private static final class ZippedDistribution implements Distribution {
    private InstalledDistribution installedDistribution;
    private final WrapperConfiguration wrapperConfiguration;
    private final Clock clock;

    private ZippedDistribution(WrapperConfiguration wrapperConfiguration, Clock clock) {
      this.wrapperConfiguration = wrapperConfiguration;
      this.clock = clock;
    }

    @Override
    public String getDisplayName() {
      return "Gradle distribution '" + wrapperConfiguration.getDistribution() + "'";
    }

    @Override
    public ClassPath getToolingImplementationClasspath(ProgressLoggerFactory progressLoggerFactory,
                                                       InternalBuildProgressListener progressListener,
                                                       ConnectionParameters connectionParameters,
                                                       BuildCancellationToken cancellationToken) {
      if (installedDistribution == null) {
        final DistributionInstaller installer = new DistributionInstaller(
          progressLoggerFactory,
          progressListener,
          clock,
          (int)Duration.ofSeconds(30).toMillis()
        );
        File installDir;
        try {
          cancellationToken.addCallback(() -> installer.cancel());
          installDir = installer.install(determineRealUserHomeDir(connectionParameters), determineRootDir(connectionParameters),
                                         wrapperConfiguration, determineSystemProperties(connectionParameters));
        }
        catch (CancellationException e) {
          throw new BuildCancelledException(
            String.format("Distribution download cancelled. Using distribution from '%s'.", wrapperConfiguration.getDistribution()), e);
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
      return installedDistribution.getToolingImplementationClasspath(progressLoggerFactory, progressListener, connectionParameters, cancellationToken);
    }

    private static Map<String, String> determineSystemProperties(ConnectionParameters connectionParameters) {
      Map<String, String> systemProperties = new HashMap<>();

      for (Map.Entry<Object, Object> objectEntry : System.getProperties().entrySet()) {
        systemProperties.put(objectEntry.getKey().toString(),
                             objectEntry.getValue() == null ? null : objectEntry.getValue().toString());
      }

      systemProperties.putAll(
        PropertiesFileHandler.getSystemProperties(new File(determineRootDir(connectionParameters), "gradle.properties")));
      systemProperties.putAll(
        PropertiesFileHandler.getSystemProperties(new File(determineRealUserHomeDir(connectionParameters), "gradle.properties")));
      return systemProperties;
    }

    private static File determineRootDir(ConnectionParameters connectionParameters) {
      return (new BuildLayoutFactory()).getLayoutFor(
        connectionParameters.getProjectDir(),
        connectionParameters.isSearchUpwards() != null ? connectionParameters.isSearchUpwards() : true).getRootDirectory();
    }

    private static File determineRealUserHomeDir(ConnectionParameters connectionParameters) {
      File distributionBaseDir = connectionParameters.getDistributionBaseDir();
      if (distributionBaseDir != null) {
        return distributionBaseDir;
      }
      else {
        File userHomeDir = connectionParameters.getGradleUserHomeDir();
        return userHomeDir != null ? userHomeDir : GradleUserHomeLookup.gradleUserHome();
      }
    }
  }

  private static void setDistributionField(GradleConnector connector, Object fieldValue)
    throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
    final Field field = connector.getClass().getDeclaredField("distribution");
    final boolean isAccessible = field.canAccess(connector);
    field.setAccessible(true);
    field.set(connector, fieldValue);
    field.setAccessible(isAccessible);
  }
}
