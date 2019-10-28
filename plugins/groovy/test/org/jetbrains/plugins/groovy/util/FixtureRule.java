// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.junit.rules.ExternalResource;

// Android Studio: translated from Kotlin to Java to work around b/111900968
public class FixtureRule extends ExternalResource {
  private final LightGroovyTestCase testCase;

  public FixtureRule(final LightProjectDescriptor descriptor, final String path) {
    this.testCase = new LightGroovyTestCase() {
      @Override
      protected LightProjectDescriptor getProjectDescriptor() {
        return descriptor;
      }
      @Override
      protected String getBasePath() {
        return TestUtils.getTestDataPath() + path;
      }
    };
  }

  public JavaCodeInsightTestFixture getFixture() {
    return testCase.getFixture();
  }

  @Override
  protected void before() throws Exception {
    testCase.setUp();
  }

  @Override
  protected void after() {
    try {
      testCase.tearDown();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
