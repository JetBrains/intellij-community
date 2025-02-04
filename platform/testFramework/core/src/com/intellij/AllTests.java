// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import junit.framework.Test;

/**
 * Run this test suite if you want to run your tests locally in the (almost) same way as TeamCity does it.
 * For the most authentic way to run tests, see [CommunityRunTestsBuildTarget] and [IdeaUltimateRunTestsBuildTarget]
 *
 * <p>
 * Specify `system.intellij.build.test.main.module` of your TC configuration as the class path of the run configuration.
 * Specify `system.intellij.build.test.groups` of your TC configuration as `-Dintellij.build.test.groups=YOUR_TEST_GROUP` VM argument.
 */
public final class AllTests {
  public static Test suite() throws Throwable {
    return new TestAll("");
  }
}
