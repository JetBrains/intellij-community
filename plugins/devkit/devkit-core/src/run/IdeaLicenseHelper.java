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
package org.jetbrains.idea.devkit.run;

import com.google.common.io.PatternFilenameFilter;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.io.IOException;

public class IdeaLicenseHelper {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.devkit.run.IdeaLicenseHelper");

  public static void copyIDEALicense(final String sandboxHome) {
    File sandboxSystemPath = new File(sandboxHome, "system");
    File systemPath = new File(PathManager.getSystemPath());
    File[] runningIdeaLicenses = systemPath.listFiles(new PatternFilenameFilter("idea\\d+\\.key"));
    if (runningIdeaLicenses != null) {
      for (File license : runningIdeaLicenses) {
        File devIdeaLicense = new File(sandboxSystemPath, license.getName());
        if (!devIdeaLicense.exists()) {
          try {
            FileUtil.copy(license, devIdeaLicense);
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
      }
    }
  }
}
