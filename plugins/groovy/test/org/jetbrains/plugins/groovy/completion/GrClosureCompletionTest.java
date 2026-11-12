// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.testFramework.junit5.TestApplication;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;

@TestApplication
public class GrClosureCompletionTest extends GrFunctionalExpressionCompletionTest {
  public GrClosureCompletionTest() {
    super(GroovyProjectDescriptors.GROOVY_1_7, "groovy/completion/");
  }
}
