// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.test.runner.events.TestEventResult;

/**
 * @author Vladislav.Soroka
 */
public class GradleSMTestProxy extends SMTestProxy {

  private @Nullable String myParentId;
  private @Nullable TestEventResult myLastResult;

  public GradleSMTestProxy(String testName, boolean isSuite, @Nullable String locationUrl) {
    super(testName, isSuite, locationUrl);
  }

  public @Nullable String getParentId() {
    return myParentId;
  }

  public void setParentId(@Nullable String parentId) {
    myParentId = parentId;
  }

  @ApiStatus.Experimental
  public @Nullable TestEventResult getLastResult() {
    return myLastResult;
  }

  @ApiStatus.Experimental
  public void setLastResult(@Nullable TestEventResult lastResult) {
    myLastResult = lastResult;
  }
}
