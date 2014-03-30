package org.jetbrains.plugins.gradle.util;

import com.intellij.ide.actions.OpenProjectFileChooserDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileTypeDescriptor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.Stack;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.GradleScript;
import org.gradle.wrapper.WrapperConfiguration;
import org.gradle.wrapper.WrapperExecutor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
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

  private static final String LAST_USED_GRADLE_HOME_KEY    = "last.used.gradle.home";
  @NonNls private static final String JVM_ARG_FORMAT = "-D%1$s=%2$s";

  private GradleUtil() {
  }

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
    return DescriptorHolder.GRADLE_BUILD_FILE_CHOOSER_DESCRIPTOR;
  }

  @NotNull
  public static FileChooserDescriptor getGradleHomeFileChooserDescriptor() {
    return DescriptorHolder.GRADLE_HOME_FILE_CHOOSER_DESCRIPTOR;
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  public static boolean isGradleDefaultWrapperFilesExist(@Nullable String gradleProjectPath) {
    return getWrapperConfiguration(gradleProjectPath) != null;
  }

  /**
   * Tries to retrieve what settings should be used with gradle wrapper for the gradle project located at the given path.
   *
   * @param gradleProjectPath  target gradle project config's (*.gradle) path or config file's directory path.
   * @return                   gradle wrapper settings should be used with gradle wrapper for the gradle project located at the given path
   *                           if any; <code>null</code> otherwise
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
   * We use this class in order to avoid static initialisation of the wrapped object - it loads number of pico container-based
   * dependencies that are unavailable to the slave gradle project, so, we don't want to get unexpected NPE there.
   */
  private static class DescriptorHolder {
    public static final FileChooserDescriptor GRADLE_BUILD_FILE_CHOOSER_DESCRIPTOR = new OpenProjectFileChooserDescriptor(true) {
      @Override
      public boolean isFileSelectable(VirtualFile file) {
        return file.getName().endsWith(GradleConstants.EXTENSION);
      }

      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        if (!super.isFileVisible(file, showHiddenFiles)) {
          return false;
        }
        return file.isDirectory() || file.getName().endsWith(GradleConstants.EXTENSION);
      }
    };

    public static final FileChooserDescriptor GRADLE_HOME_FILE_CHOOSER_DESCRIPTOR
      = new FileChooserDescriptor(false, true, false, false, false, false);
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
          if (file.isFile()) {
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
    if (gradleHomePath != null) {
      PropertiesComponent.getInstance().setValue(LAST_USED_GRADLE_HOME_KEY, gradleHomePath);
    }
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

    File[] candidates = wrapperDir.listFiles(new FileFilter() {
      @Override
      public boolean accept(File candidate) {
        return candidate.isFile() && candidate.getName().endsWith(".properties");
      }
    });
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

  @NotNull
  public static String createJvmArg(@NotNull String name, @NotNull String value) {
    return String.format(JVM_ARG_FORMAT, name, value);
  }
}
