/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.intentions;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.intentions.GrIntentionFixtureKt.grIntentionFixture;

/**
 * @author Andreas Arledal
 */
@TestApplication
public class ReplaceTernaryWithIfElseTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GrIntentionFixture> grIntention =
    grIntentionFixture(projectFixture, GroovyIntentionsBundle.message("replace.ternary.with.if.else.intention.name"), null);

  @Test
  public void testDoNotTriggerOnIncompleteTernary() {
    grIntention.get().doAntiTest("""
                 return aaa ? <caret>bbb
                 """);
  }

  @Test
  public void testSimpleCondition() {
    grIntention.get().doTextTest("""
                 return aaa <caret>? bbb : ccc
                 """, """
                 if (aaa)<caret> {
                     return bbb
                 } else {
                     return ccc
                 }
                 """);
  }

  @Test
  public void testCaretAfterQuestionMark() {
    grIntention.get().doTextTest("""
                 return aaa ?<caret> bbb : ccc
                 """, """
                 if (aaa)<caret> {
                     return bbb
                 } else {
                     return ccc
                 }
                 """);
  }

  @Test
  public void testCaretInfrontOfConditional() {
    grIntention.get().doTextTest("""
                 return <caret>aaa ? bbb : ccc
                 """, """
                 if (aaa)<caret> {
                     return bbb
                 } else {
                     return ccc
                 }
                 """);
  }

  @Test
  public void testCaretInfrontOfElse() {
    grIntention.get().doTextTest("""
                 return aaa ? bbb <caret>: ccc
                 """, """
                 if (aaa)<caret> {
                     return bbb
                 } else {
                     return ccc
                 }
                 """);
  }

  @Test
  public void testCaretAfterElse() {
    grIntention.get().doTextTest("""
                 return aaa ? bbb :<caret> ccc
                 """, """
                 if (aaa)<caret> {
                     return bbb
                 } else {
                     return ccc
                 }
                 """);
  }

  @Test
  public void testCaretBeforeElseReturn() {
    grIntention.get().doTextTest("""
                 return aaa ? bbb : <caret>ccc
                 """, """
                 if (aaa)<caret> {
                     return bbb
                 } else {
                     return ccc
                 }
                 """);
  }

  @Test
  public void testCaretBeforeReturnStatement() {
    grIntention.get().doTextTest("""
                 <caret>return aaa ? bbb : ccc
                 """, """
                 if (aaa)<caret> {
                     return bbb
                 } else {
                     return ccc
                 }
                 """);
  }
}
