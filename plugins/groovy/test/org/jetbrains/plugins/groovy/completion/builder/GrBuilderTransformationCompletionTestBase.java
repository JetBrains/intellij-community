// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion.builder;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.completion.CompletionResult;
import org.jetbrains.plugins.groovy.completion.GroovyCompletionTestBase;

import static com.intellij.codeInsight.completion.CompletionType.BASIC;

public abstract class GrBuilderTransformationCompletionTestBase extends GroovyCompletionTestBase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }

  public void doCompletionTest(String text, String... args) {
    doCompletionTest(text, CompletionResult.contain, args);
  }

  public void doCompletionTest(String text, CompletionResult cr, String... args) {
    doVariantableTest(text, "", BASIC, cr, 1, args);
  }
}
