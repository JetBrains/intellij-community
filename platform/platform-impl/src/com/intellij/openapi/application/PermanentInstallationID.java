/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.application;

import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Locale;
import java.util.UUID;
import java.util.prefs.Preferences;

/**
 * UUID identifying pair user@computer
 */
public class PermanentInstallationID {
  private static final String OLD_USER_ON_MACHINE_ID_KEY = "JetBrains.UserIdOnMachine";
  private static final String INSTALLATION_ID_KEY = "user_id_on_machine";
  private static final String INSTALLATION_ID = calculateInstallationId();

  @NotNull
  public static String get() {
    return INSTALLATION_ID;
  }

  private static String calculateInstallationId() {
    final ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();
    final Preferences oldPrefs = Preferences.userRoot();
    final String oldValue = appInfo.isVendorJetBrains()? oldPrefs.get(OLD_USER_ON_MACHINE_ID_KEY, null) : null; // compatibility with previous versions

    final String companyName = appInfo.getShortCompanyName();
    final Preferences prefs = Preferences.userRoot().node(StringUtil.isEmptyOrSpaces(companyName)? "jetbrains" : companyName.toLowerCase(Locale.US));

    String installationId = prefs.get(INSTALLATION_ID_KEY, null);
    if (StringUtil.isEmptyOrSpaces(installationId)) {
      installationId = !StringUtil.isEmptyOrSpaces(oldValue) ? oldValue : UUID.randomUUID().toString();
      prefs.put(INSTALLATION_ID_KEY, installationId);
    }

    if (!appInfo.isVendorJetBrains()) {
      return installationId;
    }

    // for Windows attempt to use PermanentUserId, so that DotNet products and IDEA would use the same ID.
    if (SystemInfo.isWindows) {
      final String appdata = System.getenv("APPDATA");
      if (appdata != null) {
        final File dir = new File(appdata, "JetBrains");
        if (dir.exists() || dir.mkdirs()) {
          final File permanentIdFile = new File(dir, "PermanentUserId");
          try {
            String fromFile = "";
            if (permanentIdFile.exists()) {
              fromFile = loadFromFile(permanentIdFile).trim();
            }
            if (!fromFile.isEmpty()) {
              if (!fromFile.equals(installationId)) {
                installationId = fromFile;
                prefs.put(INSTALLATION_ID_KEY, installationId);
              }
            }
            else {
              writeToFile(permanentIdFile, installationId);
            }
          }
          catch (IOException ignored) { }
        }
      }
    }

    // make sure values in older location and in the new location are the same
    if (!installationId.equals(oldValue)) {
      oldPrefs.put(OLD_USER_ON_MACHINE_ID_KEY, installationId);
    }

    return installationId;
  }

  @NotNull
  private static String loadFromFile(@NotNull File file) throws IOException {
    try (FileInputStream is = new FileInputStream(file)) {
      final byte[] bytes = FileUtilRt.loadBytes(is);
      final int offset = CharsetToolkit.hasUTF8Bom(bytes) ? CharsetToolkit.UTF8_BOM.length : 0;
      return new String(bytes, offset, bytes.length - offset, CharsetToolkit.UTF8_CHARSET);
    }
  }

  private static void writeToFile(@NotNull File file, @NotNull String text) throws IOException {
    try (DataOutputStream stream = new DataOutputStream(new FileOutputStream(file))) {
      stream.write(text.getBytes(CharsetToolkit.UTF8_CHARSET));
    }
  }
}