// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic;

import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.prefs.Preferences;

import static com.intellij.ide.plugins.PluginManagerCoreKt.isPlatformOrJetBrainsBundled;

public final class DeviceIdManager {
  private static final Logger LOG = Logger.getInstance(DeviceIdManager.class);

  private static final String PREFERENCE_KEY = "device_id";
  private static final String SHARED_FILE_NAME = "PermanentDeviceId";

  public static @NotNull String getOrGenerateId(@Nullable DeviceIdToken token, @NotNull String recorderId) throws InvalidDeviceIdTokenException {
    assertAllowed(token, recorderId);

    var appInfo = ApplicationInfoImpl.getShadowInstance();
    var preferences = getPreferences(appInfo);
    var preferenceKey = getPreferenceKey(recorderId);
    var deviceId = preferences.get(preferenceKey, "");

    if (deviceId.isBlank()) {
      deviceId = generateId(LocalDate.now(), getOsCode());
      preferences.put(preferenceKey, deviceId);
      LOG.info("Generating new Device ID for '" + recorderId + "'");
    }

    if (appInfo.isVendorJetBrains() && OS.CURRENT == OS.Windows) {
      if (isBaseRecorder(recorderId)) {
        deviceId = syncWithSharedFile(SHARED_FILE_NAME, deviceId, preferences, preferenceKey);
      }
      else {
        deleteLegacySharedFile(recorderId + '_' + SHARED_FILE_NAME);
      }
    }

    return deviceId;
  }

  private static void assertAllowed(@Nullable DeviceIdToken token, String recorderId) throws InvalidDeviceIdTokenException {
    if (isBaseRecorder(recorderId)) {
      if (token == null) {
        throw new InvalidDeviceIdTokenException("Cannot access base device id from unknown class");
      }
      else if (!isPlatformOrJetBrainsBundled(token.getClass())) {
        throw new InvalidDeviceIdTokenException("Cannot access base device id from " + token.getClass().getName());
      }
    }
    else if (!isUndefinedRecorder(recorderId)) {
      if (token == null) {
        throw new InvalidDeviceIdTokenException("Cannot access device id from unknown class");
      }
    }
  }

  private static String getPreferenceKey(String recorderId) {
    return isBaseRecorder(recorderId) ? PREFERENCE_KEY : recorderId.toLowerCase(Locale.ROOT) + "_" + PREFERENCE_KEY;
  }

  private static boolean isBaseRecorder(String recorderId) {
    return "FUS".equals(recorderId);
  }

  private static boolean isUndefinedRecorder(String recorderId) {
    return "UNDEFINED".equals(recorderId);
  }

  @SuppressWarnings({"SameParameterValue", "DuplicatedCode"})
  private static String syncWithSharedFile(String fileName, String installationId, Preferences preferences, String prefKey) {
    var appdata = System.getenv("APPDATA");
    if (appdata != null) {
      try {
        var permanentIdFile = Path.of(appdata, "JetBrains", fileName);
        try {
          var bytes = Files.readAllBytes(permanentIdFile);
          var offset = CharsetToolkit.hasUTF8Bom(bytes) ? CharsetToolkit.UTF8_BOM.length : 0;
          var fromFile = Strings.trimEnd(new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8), '\0');
          if (!fromFile.equals(installationId) && isValid(fromFile)) {
            preferences.put(prefKey, fromFile);
            return fromFile;
          }
        }
        catch (NoSuchFileException | IllegalArgumentException ignored) { }
        Files.createDirectories(permanentIdFile.getParent());
        Files.writeString(permanentIdFile, installationId);
      }
      catch (IOException ignored) { }
    }
    return installationId;
  }

  private static boolean isValid(String id) {
    return id.length() >= 30 && id.length() <= 50 && id.chars().allMatch(c -> c == '-' || Character.isLetterOrDigit(c));
  }

  private static void deleteLegacySharedFile(String fileName) {
    var appdata = System.getenv("APPDATA");
    if (appdata != null) {
      try {
        Files.deleteIfExists(Path.of(appdata, "JetBrains", fileName));
      }
      catch (Exception ignored) { }
    }
  }

  private static Preferences getPreferences(ApplicationInfoEx appInfo) {
    var companyName = appInfo.getShortCompanyName();
    var nodeName = companyName == null || companyName.isBlank() ? "jetbrains" : companyName.toLowerCase(Locale.ROOT);
    return Preferences.userRoot().node(nodeName);
  }

  /**
   * Device ID is generated by concatenating the following values:
   * <ul>
   *   <li>the current date in "ddMMyy" format with the year coerced between 2000 and 2099</li>
   *   <li>an OS code (see {@link #getOsCode()}</li>
   *   <li>a string representation of {@link UUID#randomUUID()}</li>
   * </ul>
   */
  @VisibleForTesting
  public static @NotNull String generateId(@NotNull LocalDate date, char osCode) {
    var coercedDate = date.withYear(Math.min(Math.max(date.getYear(), 2000), 2099));
    return coercedDate.format(DateTimeFormatter.ofPattern("ddMMyy")) + osCode + UUID.randomUUID();
  }

  private static char getOsCode() {
    return switch (OS.CURRENT) {
      case Windows -> '1';
      case macOS -> '2';
      case Linux -> '3';
      default -> '0';
    };
  }

  /**
   * Marker interface used to identify the client which retrieves a device ID.
   */
  public interface DeviceIdToken { }

  public static class InvalidDeviceIdTokenException extends Exception {
    private InvalidDeviceIdTokenException(String message) {
      super(message);
    }
  }
}
