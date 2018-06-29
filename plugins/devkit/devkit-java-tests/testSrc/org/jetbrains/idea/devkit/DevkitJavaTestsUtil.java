// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit;

import com.intellij.openapi.application.PluginPathManager;

public final class DevkitJavaTestsUtil {
  public static final String TESTDATA_PATH = PluginPathManager.getPluginHomePathRelative("devkit") + "/devkit-java-tests/testData/";
  public static final String TESTDATA_ABSOLUTE_PATH = PluginPathManager.getPluginHomePath("devkit") + "/devkit-java-tests/testData/";
}