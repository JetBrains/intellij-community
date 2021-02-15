// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.run;

import com.google.common.io.PatternFilenameFilter;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;

public final class IdeaLicenseHelper {
  private static final Logger LOG = Logger.getInstance(IdeaLicenseHelper.class);

  private static final @NonNls String IDEA_KEY_FILE_PATTERN = "idea\\d+\\.key";

  public static void copyIDEALicense(final String sandboxHome) {
    File sandboxSystemPath = new File(sandboxHome, "system");
    File systemPath = new File(PathManager.getSystemPath());
    File[] runningIdeaLicenses = systemPath.listFiles(new PatternFilenameFilter(IDEA_KEY_FILE_PATTERN));
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
