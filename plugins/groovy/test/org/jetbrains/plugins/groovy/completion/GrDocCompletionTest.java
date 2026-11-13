// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.completion.GroovyCompletionFixtureKt.groovyCompletionFixture;

/**
 * @author Max Medvedev
 */
@TestApplication
public class GrDocCompletionTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture();
  private final TestFixture<GroovyCompletionFixture> completion = groovyCompletionFixture(project, TestUtils.getAbsoluteTestDataPath() + "groovy/completion/gdoc");

  @Test
  public void testLinkCompletion() {
    completion.get().doBasicTest();
  }

  @Test
  public void testLinkCompletion1() {
    ReadAction.run(() -> CodeStyle.getSettings(completion.get().getFixture().getProject()).getCustomSettings(GroovyCodeStyleSettings.class).USE_FQ_CLASS_NAMES_IN_JAVADOC = false);
    completion.get().doBasicTest();
  }
}
