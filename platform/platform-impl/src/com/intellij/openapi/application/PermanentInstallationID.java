// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
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
    final Preferences prefs = Preferences.userRoot().node(StringUtil.isEmptyOrSpaces(companyName)? "jetbrains" : StringUtil.toLowerCase(companyName));

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
      installationId = syncWithSharedFile("PermanentUserId", installationId, prefs, INSTALLATION_ID_KEY);
    }

    // make sure values in older location and in the new location are the same
    if (!installationId.equals(oldValue)) {
      oldPrefs.put(OLD_USER_ON_MACHINE_ID_KEY, installationId);
    }

    return installationId;
  }

  @NotNull
  public static String syncWithSharedFile(@NotNull String fileName,
                                          @NotNull String installationId,
                                          @NotNull Preferences prefs,
                                          @NotNull String prefsKey) {
    final String appdata = System.getenv("APPDATA");
    if (appdata != null) {
      final File dir = new File(appdata, "JetBrains");
      if (dir.exists() || dir.mkdirs()) {
        final File permanentIdFile = new File(dir, fileName);
        try {
          String fromFile = "";
          if (permanentIdFile.exists()) {
            fromFile = loadFromFile(permanentIdFile).trim();
          }
          if (!fromFile.isEmpty()) {
            if (!fromFile.equals(installationId)) {
              installationId = fromFile;
              prefs.put(prefsKey, installationId);
            }
          }
          else {
            writeToFile(permanentIdFile, installationId);
          }
        }
        catch (IOException ignored) { }
      }
    }
    return installationId;
  }

  @NotNull
  private static String loadFromFile(@NotNull File file) throws IOException {
    try (FileInputStream is = new FileInputStream(file)) {
      final byte[] bytes = FileUtilRt.loadBytes(is);
      final int offset = CharsetToolkit.hasUTF8Bom(bytes) ? CharsetToolkit.UTF8_BOM.length : 0;
      return new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
    }
  }

  private static void writeToFile(@NotNull File file, @NotNull String text) throws IOException {
    try (DataOutputStream stream = new DataOutputStream(new FileOutputStream(file))) {
      stream.write(text.getBytes(StandardCharsets.UTF_8));
    }
  }
}