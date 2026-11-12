// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.closure.eachToFor;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.intentions.GrIntentionFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.intentions.GrIntentionFixtureKt.grIntentionFixture;
import static org.jetbrains.plugins.groovy.intentions.closure.EachToForIntention.HINT;

@TestApplication
public class EachWithIndexToForIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GrIntentionFixture> grIntention =
    grIntentionFixture(projectFixture, HINT, TestUtils.getAbsoluteTestDataPath() + "intentions/EachWithIndexToFor/");

  @Test
  public void testEachToFor() { grIntention.get().doTest(true); }

  @Test
  public void testEachToForWithFinal() { grIntention.get().doTest(true); }

  @Test
  public void testEachForInWithNoQualifier() { grIntention.get().doTest(true); }

  @Test
  public void testWithClosureInBody() { grIntention.get().doTest(true); }

  @Test
  public void testUpdateReturn() { grIntention.get().doTest(true); }

  @Test
  public void testUpdateReturn2() { grIntention.get().doTest(true); }
}
