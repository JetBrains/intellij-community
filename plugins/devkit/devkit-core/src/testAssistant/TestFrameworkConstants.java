// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.testAssistant;

import org.jetbrains.annotations.NonNls;

public final class TestFrameworkConstants {
  
  @NonNls
  public static final String PARAMETERIZED_ANNOTATION_QUALIFIED_NAME = "com.intellij.testFramework.Parameterized";

  @NonNls
  public static final String TEST_DATA_FILE_ANNOTATION_QUALIFIED_NAME = "com.intellij.testFramework.TestDataFile";

  @NonNls
  public static final String TEST_DATA_PATH_ANNOTATION_QUALIFIED_NAME = "com.intellij.testFramework.TestDataPath";

  @NonNls
  public static final String CONTENT_ROOT_VARIABLE = "$CONTENT_ROOT";
  @NonNls
  public static final String PROJECT_ROOT_VARIABLE = "$PROJECT_ROOT";
}
