/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.junit;

import com.intellij.openapi.roots.ExternalLibraryDescriptor;

/**
 * @author nik
 */
public class JUnitExternalLibraryDescriptor extends ExternalLibraryDescriptor {
  public static final ExternalLibraryDescriptor JUNIT3 = new JUnitExternalLibraryDescriptor("3", "3.8.2");
  public static final ExternalLibraryDescriptor JUNIT4 = new JUnitExternalLibraryDescriptor("4", "4.12");
  public static final ExternalLibraryDescriptor JUNIT5 = new JUnitExternalLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", "5.2",
                                                                                            null);
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

  @Override
  public String getPresentableName() {
    return "JUnit" + myVersion;
  }
}
