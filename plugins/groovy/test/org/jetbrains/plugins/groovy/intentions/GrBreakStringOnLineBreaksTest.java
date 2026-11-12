// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.intentions.GrIntentionFixtureKt.grIntentionFixture;

/**
 * @author Max Medvedev
 */
@TestApplication
public class GrBreakStringOnLineBreaksTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GrIntentionFixture> grIntention =
    grIntentionFixture(projectFixture, GroovyIntentionsBundle.message("gr.break.string.on.line.breaks.intention.name"), TestUtils.getAbsoluteTestDataPath() + "intentions/breakStringOnLineBreaks/");

  @Test
  public void testSimple() {
    grIntention.get().doTextTest("""
                 print 'ab<caret>c\\ncde'""", """
                 print 'abc\\n' +
                         'cde'""");
  }

  @Test
  public void testNR() {
    grIntention.get().doTextTest("""
                 print 'ab<caret>c\\n\\rcde'""", """
                 print 'abc\\n\\r' +
                         'cde'""");
  }

  @Test
  public void testGString() {
    grIntention.get().doTextTest("""
                 print "a<caret>\\n$x bc\\n\"""", """
                 print "a\\n" +
                         "$x bc\\n\"""");
  }
}
