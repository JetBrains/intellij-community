// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.intentions.GrIntentionFixtureKt.grIntentionFixture;

@TestApplication
public class GrFlipCommaTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GrIntentionFixture> grIntention =
    grIntentionFixture(projectFixture, CodeInsightBundle.message("intention.name.flip"), null);

  @Test
  public void testFlipFirstAndMiddleParameters() {
    grIntention.get().doTextTest("def m(String a<caret>, int b, boolean c) {}",
               "def m(int b<caret>, String a, boolean c) {}");
    grIntention.get().doTextTest("def m(String a,<caret> int b, boolean c) {}",
               "def m(int b, String a, boolean c) {}");
  }

  @Test
  public void testFlipMiddleAndLastParameters() {
    grIntention.get().doTextTest("def m(String a, int b<caret>, boolean c) {}",
               "def m(String a, boolean c<caret>, int b) {}");
    grIntention.get().doTextTest("def m(String a, int b,<caret> boolean c) {}",
               "def m(String a, boolean c, int b) {}");
  }

  @Test
  public void testFlipFirstAndMiddleListElements() {
    grIntention.get().doTextTest("[1<caret>, 2, 3]",
               "[2<caret>, 1, 3]");
    grIntention.get().doTextTest("[1,<caret> 2, 3]",
               "[2, 1, 3]");
  }

  @Test
  public void testFlipMiddleAndLastListElements() {
    grIntention.get().doTextTest("[1, 2<caret>, 3]",
               "[1, 3<caret>, 2]");
    grIntention.get().doTextTest("[1, 2,<caret> 3]",
               "[1, 3, 2]");
  }

  @Test
  public void testFlipAfterNewline() {
    grIntention.get().doTextTest("[1<caret>,\n 2]",
               "[2<caret>,\n 1]");
  }
}
