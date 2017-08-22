/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.util;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileTypeDescriptor;
import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.Stack;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.GradleScript;
import org.gradle.wrapper.WrapperConfiguration;
import org.gradle.wrapper.WrapperExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Properties;

/**
 * Holds miscellaneous utility methods.
 *
 * @author Denis Zhdanov
 * @since 8/25/11 1:19 PM
 */
public class GradleUtil {
  private static final String LAST_USED_GRADLE_HOME_KEY = "last.used.gradle.home";

  private GradleUtil() { }

  /**
   * Allows to retrieve file chooser descriptor that filters gradle scripts.
   * <p/>
   * <b>Note:</b> we want to fall back to the standard {@link FileTypeDescriptor} when dedicated gradle file type
   * is introduced (it's processed as groovy file at the moment). We use open project descriptor here in order to show
   * custom gradle icon at the file chooser ({@link icons.GradleIcons#Gradle}, is used at the file chooser dialog via
   * the dedicated gradle project open processor).
   */
  @NotNull
  public static FileChooserDescriptor getGradleProjectFileChooserDescriptor() {
    return FileChooserDescriptorFactory.createSingleFileDescriptor(GradleConstants.EXTENSION);
  }

  @NotNull
  public static FileChooserDescriptor getGradleHomeFileChooserDescriptor() {
    return FileChooserDescriptorFactory.createSingleFolderDescriptor();
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  public static boolean isGradleDefaultWrapperFilesExist(@Nullable String gradleProjectPath) {
    return getWrapperConfiguration(gradleProjectPath) != null;
  }

  /**
   * Tries to retrieve what settings should be used with gradle wrapper for the gradle project located at the given path.
   *
   * @param gradleProjectPath  target gradle project config (*.gradle) path or config file's directory path.
   * @return                   gradle wrapper settings should be used with gradle wrapper for the gradle project located at the given path
   *                           if any; {@code null} otherwise
   */
  @Nullable
  public static WrapperConfiguration getWrapperConfiguration(@Nullable String gradleProjectPath) {
    final File wrapperPropertiesFile = findDefaultWrapperPropertiesFile(gradleProjectPath);
    if (wrapperPropertiesFile == null) return null;

    final WrapperConfiguration wrapperConfiguration = new WrapperConfiguration();
    final Properties props = new Properties();
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new FileReader(wrapperPropertiesFile));
      props.load(reader);
      String distributionUrl = props.getProperty(WrapperExecutor.DISTRIBUTION_URL_PROPERTY);
      if(StringUtil.isEmpty(distributionUrl)) {
        throw new ExternalSystemException("Wrapper 'distributionUrl' property does not exist!");
      } else {
        wrapperConfiguration.setDistribution(new URI(distributionUrl));
      }
      String distributionPath = props.getProperty(WrapperExecutor.DISTRIBUTION_PATH_PROPERTY);
      if(!StringUtil.isEmpty(distributionPath)) {
        wrapperConfiguration.setDistributionPath(distributionPath);
      }
      String distPathBase = props.getProperty(WrapperExecutor.DISTRIBUTION_BASE_PROPERTY);
      if(!StringUtil.isEmpty(distPathBase)) {
        wrapperConfiguration.setDistributionBase(distPathBase);
      }
      return wrapperConfiguration;
    }
    catch (Exception e) {
      GradleLog.LOG.warn(
        String.format("I/O exception on reading gradle wrapper properties file at '%s'", wrapperPropertiesFile.getAbsolutePath()), e);
    }
    finally {
      if (reader != null) {
        try {
          reader.close();
        }
        catch (IOException e) {
          // Ignore
        }
      }
    }
    return null;
  }

  /**
   * Allows to build file system path to the target gradle sub-project given the root project path.
   *
   * @param subProject       target sub-project which config path we're interested in
   * @param rootProjectPath  path to root project's directory which contains 'build.gradle'
   * @return                 path to the given sub-project's directory which contains 'build.gradle'
   */
  @NotNull
  public static String getConfigPath(@NotNull GradleProject subProject, @NotNull String rootProjectPath) {
    try {
      GradleScript script = subProject.getBuildScript();
      if (script != null) {
        File file = script.getSourceFile();
        if (file != null) {
          if (!file.isDirectory()) {
            // The file points to 'build.gradle' at the moment but we keep it's parent dir path instead.
            file = file.getParentFile();
          }
          return ExternalSystemApiUtil.toCanonicalPath(file.getCanonicalPath());
        }
      }
    }
    catch (Exception e) {
      // As said by gradle team: 'One thing I'm interested in is whether you have any thoughts about how the tooling API should
      // deal with missing details from the model - for example, when asking for details about the build scripts when using
      // a version of Gradle that does not supply that information. Currently, you'll get a `UnsupportedOperationException`
      // when you call the `getBuildScript()` method'.
      //
      // So, just ignore it and assume that the user didn't define any custom build file name.
    }
    File rootProjectParent = new File(rootProjectPath);
    StringBuilder buffer = new StringBuilder(FileUtil.toCanonicalPath(rootProjectParent.getAbsolutePath()));
    Stack<String> stack = ContainerUtilRt.newStack();
    for (GradleProject p = subProject; p != null; p = p.getParent()) {
      stack.push(p.getName());
    }

    // pop root project
    stack.pop();
    while (!stack.isEmpty()) {
      buffer.append(ExternalSystemConstants.PATH_SEPARATOR).append(stack.pop());
    }
    return buffer.toString();
  }

  @NotNull
  public static String getLastUsedGradleHome() {
    return PropertiesComponent.getInstance().getValue(LAST_USED_GRADLE_HOME_KEY, "");
  }

  public static void storeLastUsedGradleHome(@Nullable String gradleHomePath) {
    PropertiesComponent.getInstance().setValue(LAST_USED_GRADLE_HOME_KEY, gradleHomePath, null);
  }

  @Nullable
  public static File findDefaultWrapperPropertiesFile(@Nullable String gradleProjectPath) {
    if (gradleProjectPath == null) {
      return null;
    }
    File file = new File(gradleProjectPath);

    // There is a possible case that given path points to a gradle script (*.gradle) but it's also possible that
    // it references script's directory. We want to provide flexibility here.
    File gradleDir;
    if (file.isFile()) {
      gradleDir = new File(file.getParentFile(), "gradle");
    }
    else {
      gradleDir = new File(file, "gradle");
    }
    if (!gradleDir.isDirectory()) {
      return null;
    }

    File wrapperDir = new File(gradleDir, "wrapper");
    if (!wrapperDir.isDirectory()) {
      return null;
    }

    File[] candidates = wrapperDir.listFiles(FileFilters.filesWithExtension("properties"));
    if (candidates == null) {
      GradleLog.LOG.warn("No *.properties file is found at the gradle wrapper directory " + wrapperDir.getAbsolutePath());
      return null;
    }
    else if (candidates.length != 1) {
      GradleLog.LOG.warn(String.format(
        "%d *.properties files instead of one have been found at the wrapper directory (%s): %s",
        candidates.length, wrapperDir.getAbsolutePath(), Arrays.toString(candidates)
      ));
      return null;
    }

    return candidates[0];
  }
}
