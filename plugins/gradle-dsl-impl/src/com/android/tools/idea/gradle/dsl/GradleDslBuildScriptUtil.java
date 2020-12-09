// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.gradle.dsl;

import java.io.File;
import org.jetbrains.annotations.NotNull;

/*
This class use some methods from com.android.utils.BuildScriptUtil
To be removed in future
 */
public final class GradleDslBuildScriptUtil {
  public static File findGradleSettingsFile(@NotNull File dirPath) {
    File groovySettingsFile = new File(dirPath, "settings.gradle");
    if (groovySettingsFile.isFile()) {
      return groovySettingsFile;
    }
    else {
      File kotlinSettingsFile = new File(dirPath, "settings.gradle.kts");
      return kotlinSettingsFile.isFile() ? kotlinSettingsFile : groovySettingsFile;
    }
  }

  public static File findGradleBuildFile(@NotNull File dirPath) {
    File groovyBuildFile = new File(dirPath, "build.gradle");
    if (groovyBuildFile.isFile()) {
      return groovyBuildFile;
    }
    else {
      File kotlinBuildFile = new File(dirPath, "build.gradle.kts");
      return kotlinBuildFile.isFile() ? kotlinBuildFile : groovyBuildFile;
    }
  }
}
