// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import org.jetbrains.annotations.NotNull;

public final class JUnitExternalLibraryDescriptor extends ExternalLibraryDescriptor {
  public static final ExternalLibraryDescriptor JUNIT3 = new JUnitExternalLibraryDescriptor("junit", "junit", "3", "3.8.2", "3.0");
  public static final ExternalLibraryDescriptor JUNIT4 = new JUnitExternalLibraryDescriptor("junit", "junit", "4", "4.13.1", "4.0");
  public static final ExternalLibraryDescriptor JUNIT5 = new JUnitExternalLibraryDescriptor("org.junit.jupiter", "junit-jupiter", "5", "5.14.0", "5.0.0");
  public static final ExternalLibraryDescriptor JUNIT6 = new JUnitExternalLibraryDescriptor("org.junit.jupiter", "junit-jupiter", "6", "6.0.0", "6.0.0");
  private final String myVersion;

  private JUnitExternalLibraryDescriptor(@NotNull String groupId,
                                         @NotNull String artifactId,
                                         @NotNull String version,
                                         @NotNull String preferredVersion,
                                         @NotNull String minVersion) {
    super(groupId, artifactId, minVersion, version + ".999", preferredVersion);
    myVersion = version;
  }

  @Override
  public @NotNull String getPresentableName() {
    return "JUnit" + myVersion;
  }
}
