package org.jetbrains.plugins.gradle.util;

import com.intellij.ide.actions.OpenProjectFileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileTypeDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Arrays;
import java.util.Properties;

/**
 * Holds miscellaneous utility methods.
 *
 * @author Denis Zhdanov
 * @since 8/25/11 1:19 PM
 */
public class GradleUtil {

  private static final String  WRAPPER_VERSION_PROPERTY_KEY = "distributionUrl";

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
  public static boolean isGradleWrapperDefined(@Nullable String gradleProjectPath) {
    return !StringUtil.isEmpty(getWrapperDistribution(gradleProjectPath));
  }

  /**
   * Tries to parse what gradle version should be used with gradle wrapper for the gradle project located at the given path. 
   *
   * @param gradleProjectPath  target gradle project path
   * @return gradle version should be used with gradle wrapper for the gradle project located at the given path
   *                           if any; <code>null</code> otherwise
   */
  @Nullable
  public static String getWrapperDistribution(@Nullable String gradleProjectPath) {
    if (gradleProjectPath == null) {
      return null;
    }
    File file = new File(gradleProjectPath);
    if (!file.isFile()) {
      return null;
    }

    File gradleDir = new File(file.getParentFile(), "gradle");
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

    Properties props = new Properties();
    BufferedReader reader = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      reader = new BufferedReader(new FileReader(candidates[0]));
      props.load(reader);
      String distribution = props.getProperty(WRAPPER_VERSION_PROPERTY_KEY);
      if (StringUtil.isEmpty(distribution)) {
        return null;
      }
      String shortName = StringUtil.getShortName(distribution, '/');
      return StringUtil.trimEnd(shortName, ".zip");
    }
    catch (IOException e) {
      GradleLog.LOG.warn(
        String.format("I/O exception on reading gradle wrapper properties file at '%s'", candidates[0].getAbsolutePath()),
        e
      );
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
        return GradleConstants.DEFAULT_SCRIPT_NAME.equals(file.getName());
      }

      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        if (!super.isFileVisible(file, showHiddenFiles)) {
          return false;
        }
        return file.isDirectory() || GradleConstants.DEFAULT_SCRIPT_NAME.equals(file.getName());
      }
    };

    public static final FileChooserDescriptor GRADLE_HOME_FILE_CHOOSER_DESCRIPTOR
      = new FileChooserDescriptor(false, true, false, false, false, false);
  }
}
