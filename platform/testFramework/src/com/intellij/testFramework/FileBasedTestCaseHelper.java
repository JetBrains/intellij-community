// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Should be implemented by a test together with annotation @RunWith(com.intellij.testFramework.Parameterized.class)
 * in order to get test run on all test data files located in directory. The desired directory could be configured 
 * whether by implementing {@link FileBasedTestCaseHelperEx#getRelativeBasePath()} or by annotating test case 
 * with {@link TestDataPath} (annotation would enable additional test assistance support, e.g. 
 * navigation from test data to test class/method as well as starting tests right from test data files). 
 * <br/><br/>
 * BTW @RunWith works also on abstract super classes.
 * @see LightPlatformCodeInsightTestCase#params(Class)
 */
public interface FileBasedTestCaseHelper {
  /**
   * @return for 'before' files should return core file name or null otherwise
   */
  @Nullable
  String getFileSuffix(@NotNull String fileName);

  /**
   * @return for 'after' files should return core file name or null otherwise
   */
  default @Nullable String getBaseName(@NotNull String fileAfterSuffix) {
    return null;
  }
}
