// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic;

import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.UUID;
import java.util.prefs.Preferences;

public final class DeviceIdManager {
  private static final Logger LOG = Logger.getInstance(DeviceIdManager.class);

  private static final String DEVICE_ID_SHARED_FILE = "PermanentDeviceId";
  private static final String DEVICE_ID_PREFERENCE_KEY = "device_id";

  public static String getOrGenerateId() {
    ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();
    Preferences prefs = getPreferences(appInfo);

    String deviceId = prefs.get(DEVICE_ID_PREFERENCE_KEY, null);
    if (StringUtil.isEmptyOrSpaces(deviceId)) {
      deviceId = generateId(Calendar.getInstance(Locale.ENGLISH), getOSChar());
      prefs.put(DEVICE_ID_PREFERENCE_KEY, deviceId);
      LOG.info("Generating new Device ID");
    }

    if (appInfo.isVendorJetBrains() && SystemInfo.isWindows) {
      deviceId = syncWithSharedFile(DEVICE_ID_SHARED_FILE, deviceId, prefs, DEVICE_ID_PREFERENCE_KEY);
    }
    return deviceId;
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

  @NotNull
  private static Preferences getPreferences(@NotNull ApplicationInfoEx appInfo) {
    String companyName = appInfo.getShortCompanyName();
    String name = StringUtil.isEmptyOrSpaces(companyName) ? "jetbrains" : companyName.toLowerCase(Locale.US);
    return Preferences.userRoot().node(name);
  }

  /**
   * Device id is generating by concatenating following values:
   * Current date, written in format ddMMyy, where year coerced between 2000 and 2099
   * Character, representing user's OS (see [getOSChar])
   * [toString] call on representation of [UUID.randomUUID]
   */
  public static String generateId(@NotNull Calendar calendar, char OSChar) {
    int year = calendar.get(Calendar.YEAR);
    if (year < 2000) year = 2000;
    if (year > 2099) year = 2099;
    calendar.set(Calendar.YEAR, year);
    return new SimpleDateFormat("ddMMyy", Locale.ENGLISH).format(calendar.getTime()) + OSChar + UUID.randomUUID().toString();
  }

  private static char getOSChar() {
    if (SystemInfo.isWindows) return '1';
    else if (SystemInfo.isMac) return '2';
    else if (SystemInfo.isLinux) return '3';
    return '0';
  }
}
