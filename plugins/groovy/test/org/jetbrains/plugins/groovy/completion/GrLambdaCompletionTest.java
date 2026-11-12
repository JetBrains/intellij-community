// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;

import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyProjectFixture;

@TestApplication
public class GrLambdaCompletionTest extends GrFunctionalExpressionCompletionTest {
  private static final TestFixture<Project> project = groovyProjectFixture();

  public GrLambdaCompletionTest() {
    super(project, GroovyProjectDescriptors.GROOVY_3_0, "groovy/completion/lambda");
  }
}
