// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal;

public abstract class UseJBColorInspectionTestBase extends AbstractUseJBColorTestBase {

  protected void doTest() {
    myFixture.testHighlighting(getTestName(false) + '.' + getFileExtension());
  }
}
