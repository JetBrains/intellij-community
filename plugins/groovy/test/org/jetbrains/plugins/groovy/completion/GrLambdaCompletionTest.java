// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;


@TestApplication
public class GrLambdaCompletionTest extends GrFunctionalExpressionCompletionTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_3_0);

  public GrLambdaCompletionTest() {
    super(project, "groovy/completion/lambda");
  }
}
