// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.prefs.Preferences;

/**
 * UUID identifying pair user@computer
 */
public final class PermanentInstallationID {
  private static final Logger LOG = Logger.getInstance("#PermanentInstallationID");

  private static final String OLD_USER_ON_MACHINE_ID_KEY = "JetBrains.UserIdOnMachine";
  private static final String INSTALLATION_ID_KEY = "user_id_on_machine";
  private static final String INSTALLATION_ID = calculateInstallationId();

  public static @NotNull String get() {
    return INSTALLATION_ID;
  }

  private static String calculateInstallationId() {
    var installationId = "";

    try {
      var appInfo = ApplicationInfoImpl.getShadowInstance();
      var oldPreferences = Preferences.userRoot();
      var oldValue = appInfo.isVendorJetBrains() ? oldPreferences.get(OLD_USER_ON_MACHINE_ID_KEY, "") : ""; // compatibility with previous versions

      var companyName = appInfo.getShortCompanyName();
      var nodeName = companyName == null || companyName.isBlank() ? "jetbrains" : companyName.toLowerCase(Locale.ROOT);
      var preferences = Preferences.userRoot().node(nodeName);

      installationId = preferences.get(INSTALLATION_ID_KEY, "");
      if (installationId.isBlank()) {
        installationId = !oldValue.isBlank() ? oldValue : UUID.randomUUID().toString();
        preferences.put(INSTALLATION_ID_KEY, installationId);
      }

      if (!appInfo.isVendorJetBrains()) {
        return installationId;
      }

      // for Windows attempt to use PermanentUserId, so that DotNet products and IDEA would use the same ID.
      if (SystemInfo.isWindows) {
        installationId = syncWithSharedFile("PermanentUserId", installationId, preferences, INSTALLATION_ID_KEY);
      }

      // make sure values in older location and in the new location are the same
      if (!installationId.equals(oldValue)) {
        oldPreferences.put(OLD_USER_ON_MACHINE_ID_KEY, installationId);
      }
    }
    catch (Throwable t) {
      // should not happen
      LOG.info("Unexpected error initializing Installation ID", t);
      if (installationId.isBlank()) {
        installationId = UUID.randomUUID().toString();
      }
    }

    return installationId;
  }

  @SuppressWarnings({"SameParameterValue", "DuplicatedCode"})
  private static String syncWithSharedFile(String fileName, String installationId, Preferences preferences, String prefKey) {
    var appdata = System.getenv("APPDATA");
    if (appdata != null) {
      try {
        var permanentIdFile = Path.of(appdata, "JetBrains", fileName);
        Files.createDirectories(permanentIdFile.getParent());
        try {
          var bytes = Files.readAllBytes(permanentIdFile);
          var offset = CharsetToolkit.hasUTF8Bom(bytes) ? CharsetToolkit.UTF8_BOM.length : 0;
          var fromFile = new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
          if (!fromFile.equals(installationId)) {
            installationId = fromFile;
            preferences.put(prefKey, installationId);
          }
        }
        catch (NoSuchFileException ignored) {
          Files.writeString(permanentIdFile, installationId);
        }
      }
      catch (Throwable t) {
        LOG.info("Error synchronizing Installation ID", t);
      }
    }
    return installationId;
  }
}
