// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.strings;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.intentions.GrIntentionFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.intentions.GrIntentionFixtureKt.grIntentionFixture;

/**
 * @author Maxim.Medvedev
 */
@TestApplication
public class ConvertConcatenationToGstringTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_1_7);
  private final TestFixture<GrIntentionFixture> grIntention =
    grIntentionFixture(projectFixture, "Convert to GString", TestUtils.getAbsoluteTestDataPath() + "intentions/convertConcatenationToGstring/");

  @Test
  public void testSimpleCase() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testVeryComplicatedCase() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testQuotes() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testQuotes2() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testQuotesInMultilineString() {
    grIntention.get().doTest(true);
  }

  @Test
  public void testDot() {
    grIntention.get().doTest(true);
  }
}
