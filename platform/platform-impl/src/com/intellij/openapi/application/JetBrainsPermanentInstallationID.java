// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IntellijInternalApi;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.prefs.Preferences;

/**
 * UUID identifying pair user@computer, allowed only to JetBrains plugins.
 */
@IntellijInternalApi
@ApiStatus.Internal
public final class JetBrainsPermanentInstallationID {
  private static final String INSTALLATION_ID_KEY = "user_id_on_machine";
  private static final String INSTALLATION_ID = calculateInstallationId();

  public static @NotNull String get() {
    return INSTALLATION_ID;
  }

  private static String calculateInstallationId() {
    var installationId = "";

    try {
      var appInfo = ApplicationInfoImpl.getShadowInstance();
      var companyName = appInfo.getShortCompanyName();
      var nodeName = companyName == null || companyName.isBlank() ? "jetbrains" : companyName.toLowerCase(Locale.ROOT);
      var preferences = Preferences.userRoot().node(nodeName);

      installationId = preferences.get(INSTALLATION_ID_KEY, "");
      if (!isValid(installationId)) {
        installationId = UUID.randomUUID().toString();
        preferences.put(INSTALLATION_ID_KEY, installationId);
      }

      if (!appInfo.isVendorJetBrains()) {
        return installationId;
      }

      // on Windows, try to use the `PermanentUserId` file, for .NET and IJ products to share the same ID
      if (OS.CURRENT == OS.Windows) {
        installationId = syncWithSharedFile(installationId, preferences);
      }
    }
    catch (Throwable t) {
      // should not happen
      Logger.getInstance(JetBrainsPermanentInstallationID.class).info("Unexpected error initializing Installation ID", t);
      if (!isValid(installationId)) {
        installationId = UUID.randomUUID().toString();
      }
    }

    return installationId;
  }

  @SuppressWarnings("DuplicatedCode")
  private static String syncWithSharedFile(String installationId, Preferences preferences) {
    var appdata = System.getenv("APPDATA");
    if (appdata != null) {
      try {
        var permanentIdFile = Path.of(appdata, "JetBrains/PermanentUserId");
        try {
          var bytes = Files.readAllBytes(permanentIdFile);
          var offset = CharsetToolkit.hasUTF8Bom(bytes) ? CharsetToolkit.UTF8_BOM.length : 0;
          var fromFile = Strings.trimEnd(new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8), '\0');
          if (!fromFile.equals(installationId) && isValid(fromFile)) {
            preferences.put(INSTALLATION_ID_KEY, installationId);
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
    try {
      UUID.fromString(id);
      return true;
    }
    catch (Exception ignored) {
      return false;
    }
  }
}
