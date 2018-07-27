// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 * @since 2/25/14
 */
public class GradleSMTestProxy extends SMTestProxy {

  @Nullable private final String myClassName;
  @Nullable private String myParentId;

  public GradleSMTestProxy(String testName, boolean isSuite, @Nullable String locationUrl, @Nullable String className) {
    super(testName, isSuite, locationUrl);
    myClassName = className;
  }

  @Nullable
  public String getParentId() {
    return myParentId;
  }

  public void setParentId(@Nullable String parentId) {
    myParentId = parentId;
  }

  @Nullable
  public String getClassName() {
    return myClassName;
  }
}
