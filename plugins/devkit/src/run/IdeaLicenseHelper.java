/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.run;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.io.IOException;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: Dec 3, 2004
 */
public class IdeaLicenseHelper {
  @NonNls private static final String LICENSE_PATH_PREFERRED = "idea50.key";
  @NonNls private static final String LICENSE_PATH_40 = "idea40.key";
  @NonNls private static final String LICENSE_PATH_SYSTEM = "idea.license";

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.devkit.run.IdeaLicenseHelper");

  @Nullable
  public static File isIDEALicenseInSandbox(final String configPath, final String systemPath, final String binPath){
    final File config = new File(configPath, LICENSE_PATH_PREFERRED);
    if (config.exists()){
      return config;
    }
    final File idea4 = new File(configPath, LICENSE_PATH_40);
    if (idea4.exists()){
      return idea4;
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

  @SuppressWarnings({"HardCodedStringLiteral"})
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
