package org.jetbrains.idea.devkit.run;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.diagnostic.Logger;

import java.io.File;
import java.io.IOException;

import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;
import org.jetbrains.idea.devkit.projectRoots.Sandbox;

/**
 * User: anna
 * Date: Dec 3, 2004
 */
public class IdeaLicenseHelper {
  private static final String LICENSE_PATH_PREFERRED = "idea40.key";
  private static final String LICENSE_PATH_SYSTEM = "idea.license";

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.devkit.run.IdeaLicenseHelper");

  public static File isIDEALicenseInSandbox(final String configPath, final String systemPath, final String binPath){
    final File config = new File(configPath, LICENSE_PATH_PREFERRED);
    if (config.exists()){
      return config;
    }
    final File system = new File(systemPath, LICENSE_PATH_SYSTEM);
    if (system.exists()){
      return system;
    }
    final File bin = new File(binPath, LICENSE_PATH_SYSTEM);
    if (bin.exists()){
      return bin;
    }
    return null;
  }

  public static void copyIDEALicencse(final String sandboxHome, ProjectJdk jdk){
    if (isIDEALicenseInSandbox(sandboxHome + File.separator + "config", sandboxHome + File.separator + "system", jdk.getHomePath() + File.separator + "bin") == null){
      final File ideaLicense = isIDEALicenseInSandbox(PathManager.getConfigPath(), PathManager.getSystemPath(), PathManager.getBinPath());
      if (ideaLicense != null){
        try {
          FileUtil.copy(ideaLicense, new File(new File(sandboxHome, "config"), LICENSE_PATH_PREFERRED));
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
  }
}
