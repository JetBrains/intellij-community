// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.refactoring.move;

import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;

public abstract class GroovyMoveTestBase extends LightMultiFileTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_2_5;
  }

  protected void doTest(String newPackageName, String... names) {
    doTest(() -> perform(newPackageName, names));
  }

  abstract void perform(String newPackageName, String[] names);
}
