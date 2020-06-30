// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures;

import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author gregsh
 */
@RunWith(JUnit4.class)
public abstract class LightPlatformCodeInsightFixture4TestCase extends BasePlatformTestCase {
  @Rule
  @Override
  @NotNull
  public TestRule getRunBareTestRule() {
    return super.getRunBareTestRule();
  }
}
