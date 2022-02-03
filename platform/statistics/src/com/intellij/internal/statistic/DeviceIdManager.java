// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic;

import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.UUID;
import java.util.prefs.Preferences;

public final class DeviceIdManager {
  private static final Logger LOG = Logger.getInstance(DeviceIdManager.class);

  private static final String UNDEFINED = "UNDEFINED";
  private static final String DEVICE_ID_SHARED_FILE = "PermanentDeviceId";
  private static final String DEVICE_ID_PREFERENCE_KEY = "device_id";

  /**
   * @deprecated Use {@link DeviceIdManager#getOrGenerateId(DeviceIdToken, String)} with purpose specific id
   */
  @Deprecated
  public static String getOrGenerateId() {
    try {
      return getOrGenerateId(null, UNDEFINED);
    }
    catch (InvalidDeviceIdTokenException e) {
      LOG.error(e);
    }
    return "";
  }

  public static String getOrGenerateId(@Nullable DeviceIdToken token, @NotNull String recorderId) throws InvalidDeviceIdTokenException {
    assertAllowed(token, recorderId);

    ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();
    Preferences prefs = getPreferences(appInfo);

    String preferenceKey = getPreferenceKey(recorderId);
    String deviceId = prefs.get(preferenceKey, null);
    if (StringUtil.isEmptyOrSpaces(deviceId)) {
      deviceId = generateId(Calendar.getInstance(Locale.ENGLISH), getOSChar());
      prefs.put(preferenceKey, deviceId);
      LOG.info("Generating new Device ID for '" + recorderId + "'");
    }

    if (appInfo.isVendorJetBrains() && SystemInfo.isWindows) {
      if (isBaseRecorder(recorderId)) {
        deviceId = syncWithSharedFile(DEVICE_ID_SHARED_FILE, deviceId, prefs, preferenceKey);
      }
      else {
        deleteLegacySharedFile(recorderId + "_" + DEVICE_ID_SHARED_FILE);
      }
    }
    return deviceId;
  }

  private static void assertAllowed(@Nullable DeviceIdToken token, @NotNull String recorderId) throws InvalidDeviceIdTokenException {
    if (isBaseRecorder(recorderId)) {
      if (token == null) {
        throw new InvalidDeviceIdTokenException("Cannot access base device id from unknown class");
      }
      else if (!PluginInfoDetectorKt.isPlatformOrJetBrainsBundled(token.getClass())) {
        throw new InvalidDeviceIdTokenException("Cannot access base device id from " + token.getClass().getName());
      }
    }
    else if (!isUndefinedRecorder(recorderId)) {
      if (token == null) {
        throw new InvalidDeviceIdTokenException("Cannot access device id from unknown class");
      }
    }
  }

  @NotNull
  private static String getPreferenceKey(@NotNull String recorderId) {
    return isBaseRecorder(recorderId) ? DEVICE_ID_PREFERENCE_KEY : StringUtil.toLowerCase(recorderId) + "_" + DEVICE_ID_PREFERENCE_KEY;
  }

  private static boolean isBaseRecorder(@NotNull String recorderId) {
    return "FUS".equals(recorderId);
  }

  private static boolean isUndefinedRecorder(@NotNull String recorderId) {
    return UNDEFINED.equals(recorderId);
  }

  @SuppressWarnings("SameParameterValue")
  @NotNull
  private static String syncWithSharedFile(@NotNull String fileName,
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

  private static void deleteLegacySharedFile(@NotNull String fileName) {
    try {
      String appdata = System.getenv("APPDATA");
      if (appdata != null) {
        File dir = new File(appdata, "JetBrains");
        if (dir.exists()) {
          File permanentIdFile = new File(dir, fileName);
          if (permanentIdFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            permanentIdFile.delete();
          }
        }
      }
    }
    catch (Exception ignored) { }
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

  /**
   * Marker interface used to identify the client which retrieves device id
   */
  public interface DeviceIdToken {}

  public static class InvalidDeviceIdTokenException extends Exception {
    private InvalidDeviceIdTokenException(String message) {
      super(message);
    }
  }
}
