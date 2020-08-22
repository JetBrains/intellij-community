// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit;

import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import org.jetbrains.annotations.NotNull;

public final class JUnitExternalLibraryDescriptor extends ExternalLibraryDescriptor {
  public static final ExternalLibraryDescriptor JUNIT3 = new JUnitExternalLibraryDescriptor("3", "3.8.2");
  public static final ExternalLibraryDescriptor JUNIT4 = new JUnitExternalLibraryDescriptor("4", "4.12");
  public static final ExternalLibraryDescriptor JUNIT5 = new JUnitExternalLibraryDescriptor("org.junit.jupiter", "junit-jupiter", "5.4",
                                                                                            "5.4.2");
  private final String myVersion;

  private JUnitExternalLibraryDescriptor(String baseVersion, String preferredVersion) {
    this("junit", "junit", baseVersion, preferredVersion);
  }

  private JUnitExternalLibraryDescriptor(final String groupId,
                                         final String artifactId,
                                         final String version,
                                         String preferredVersion) {
    super(groupId, artifactId, version + ".0", version + ".999", preferredVersion);
    myVersion = version;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "JUnit" + myVersion;
  }
}
