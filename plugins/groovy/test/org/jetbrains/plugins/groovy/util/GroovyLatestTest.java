// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.util;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;

public abstract class GroovyLatestTest extends LightProjectTest {
  @Override
  public final LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_2_5;
  }

  public GroovyLatestTest(String testDataPath) {
    super(testDataPath);
  }

  public GroovyLatestTest() { }
}
