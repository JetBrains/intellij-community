// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.application.options.CodeStyle;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.jetbrains.plugins.groovy.completion.GroovyCompletionFixture.groovyCompletionFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

/**
 * @author Max Medvedev
 */
@TestApplication
public class GrDocCompletionTest {
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST, "groovy/completion/gdoc");
  private final TestFixture<GroovyCompletionTestFixture> completion = groovyCompletionFixture(groovy);

  @Test
  public void testLinkCompletion() { runInEdtAndWait(() -> { completion.get().doBasicTest(); }); }

  @Test
  public void testLinkCompletion1() {
    runInEdtAndWait(() -> {
      CodeStyle.getSettings(groovy.get().getProject()).getCustomSettings(GroovyCodeStyleSettings.class).USE_FQ_CLASS_NAMES_IN_JAVADOC = false;
      completion.get().doBasicTest();
    });
  }
}
