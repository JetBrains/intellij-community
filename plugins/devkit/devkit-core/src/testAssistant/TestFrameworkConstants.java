// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.testAssistant;

import org.jetbrains.annotations.NonNls;

public final class TestFrameworkConstants {
  
  public static final @NonNls String PARAMETERIZED_ANNOTATION_QUALIFIED_NAME = "com.intellij.testFramework.Parameterized";

  public static final @NonNls String TEST_DATA_FILE_ANNOTATION_QUALIFIED_NAME = "com.intellij.testFramework.TestDataFile";

  public static final @NonNls String TEST_DATA_PATH_ANNOTATION_QUALIFIED_NAME = "com.intellij.testFramework.TestDataPath";

  public static final @NonNls String TEST_METADATA_ANNOTATION_QUALIFIED_NAME = "org.jetbrains.kotlin.test.TestMetadata";

  public static final @NonNls String TEST_ROOT_ANNOTATION_QUALIFIED_NAME = "org.jetbrains.kotlin.test.TestRoot";

  public static final @NonNls String CONTENT_ROOT_VARIABLE = "$CONTENT_ROOT";
  public static final @NonNls String PROJECT_ROOT_VARIABLE = "$PROJECT_ROOT";
}
