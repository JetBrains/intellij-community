// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle.tooling;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shared utility for resolving IntelliJ Platform source artifact coordinates.
 * <p>
 * Used by both the Gradle daemon side ({@link IntelliJPlatformAuxiliaryArtifactProvider})
 * and the IDE side ({@code IntelliJPlatformAttachSourcesProvider}).
 */
public final class IntelliJPlatformSourceCoordinates {

  public static final String PYCHARM_COMMUNITY_SOURCES = "com.jetbrains.intellij.pycharm:pycharmPC";

  /**
   * Source coordinates for IDEA Ultimate (version-dependent: ideaIC → ideaIU → idea).
   */
  public static @NotNull String ideaUltimateSources(int majorVersion) {
    if (majorVersion >= 253) return "com.jetbrains.intellij.idea:idea";
    if (majorVersion >= 242) return "com.jetbrains.intellij.idea:ideaIU";
    return "com.jetbrains.intellij.idea:ideaIC";
  }

  /**
   * Source coordinates for IDEA Community or any other IntelliJ Platform product.
   */
  public static @NotNull String defaultPlatformSources(int majorVersion) {
    if (majorVersion >= 253) return "com.jetbrains.intellij.idea:idea";
    return "com.jetbrains.intellij.idea:ideaIC";
  }

  /**
   * Extracts the actual version from a possibly prefixed version string.
   * Strips product-code prefix (e.g., "IC-") and build metadata suffix (e.g., "+445").
   * <p>
   * Examples:
   * <ul>
   *   <li>"IC-243.21565.193" → "243.21565.193"</li>
   *   <li>"2024.1.1+445" → "2024.1.1"</li>
   *   <li>"243.21565.193" → "243.21565.193"</li>
   * </ul>
   */
  public static @NotNull String extractActualVersion(@NotNull String version) {
    int dashIndex = version.indexOf('-');
    if (dashIndex >= 0) {
      version = version.substring(dashIndex + 1);
    }
    int plusIndex = version.indexOf('+');
    if (plusIndex >= 0) {
      version = version.substring(0, plusIndex);
    }
    return version;
  }

  /**
   * Extracts the product code prefix from a version string, if present.
   * <p>
   * Examples:
   * <ul>
   *   <li>"IC-243.21565.193" → "IC"</li>
   *   <li>"243.21565.193" → null</li>
   *   <li>"2024.1.1+445" → null</li>
   * </ul>
   */
  public static @Nullable String extractProductCode(@NotNull String version) {
    int dashIndex = version.indexOf('-');
    if (dashIndex > 0) {
      String prefix = version.substring(0, dashIndex);
      // Product codes are short alphabetic strings (e.g., IC, IU, PY, PC)
      if (prefix.chars().allMatch(Character::isLetter)) {
        return prefix;
      }
    }
    return null;
  }

  /**
   * Maps a product code to the appropriate source coordinates.
   */
  public static @NotNull String sourceCoordinatesForProductCode(@NotNull String productCode, int majorVersion) {
    switch (productCode) {
      case "IU":
        return ideaUltimateSources(majorVersion);
      case "PY":
      case "PC":
        return PYCHARM_COMMUNITY_SOURCES;
      default:
        return defaultPlatformSources(majorVersion);
    }
  }

  /**
   * Extracts major version number from a version string.
   * Handles both release format ("2024.1.1") and build number format ("243.21565.193").
   *
   * @return major version number, or -1 if the version string cannot be parsed
   */
  public static int extractMajorVersion(@NotNull String version) {
    try {
      String[] parts = version.split("\\.");
      int first = Integer.parseInt(parts[0]);
      if (first >= 2000) {
        // Release version format: 2024.1.1
        int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        return (first - 2000) * 10 + minor;
      }
      else {
        // Build number format: 243.21565.193
        return first;
      }
    }
    catch (NumberFormatException e) {
      return -1;
    }
  }
}