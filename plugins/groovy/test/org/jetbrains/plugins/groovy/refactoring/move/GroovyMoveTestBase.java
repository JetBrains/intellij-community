// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.refactoring.move;

import com.intellij.refactoring.LightMultiFileTestCase;

abstract class GroovyMoveTestBase extends LightMultiFileTestCase {

  protected void doTest(String newPackageName, String... names) {
    doTest(() -> perform(newPackageName, names));
  }

  abstract void perform(String newPackageName, String[] names);
}
