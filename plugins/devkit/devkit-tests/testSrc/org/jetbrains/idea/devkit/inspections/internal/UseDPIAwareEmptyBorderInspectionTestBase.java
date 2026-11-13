// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal;

/**
 * @deprecated Use {@link UseDPIAwareEmptyBorderFixtureKt#useDPIAwareEmptyBorderFixture} in a JUnit 5 test.
 */
@Deprecated
public abstract class UseDPIAwareEmptyBorderInspectionTestBase extends AbstractUseDPIAwareEmptyBorderTestBase {

  @Override
  protected void doTest() {
    super.doTest();
  }
}
