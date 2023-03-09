// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.jvmcompat;

public class VersionMapping {
  public String javaVersionInfo;
  public String gradleVersionInfo;

  public String comment;

  public VersionMapping() { }

  public VersionMapping(String javaVersionInfo, String gradleVersionInfo) {
    this.javaVersionInfo = javaVersionInfo;
    this.gradleVersionInfo = gradleVersionInfo;
  }

  public VersionMapping(String javaVersionInfo, String gradleVersionInfo, String comment) {
    this.javaVersionInfo = javaVersionInfo;
    this.gradleVersionInfo = gradleVersionInfo;
    this.comment = comment;
  }
}

