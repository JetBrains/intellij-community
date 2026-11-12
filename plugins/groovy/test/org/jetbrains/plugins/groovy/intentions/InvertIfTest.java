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
 * @author Niels Harremoes
 */
@TestApplication
public class InvertIfTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GrIntentionFixture> grIntention =
    grIntentionFixture(projectFixture, GroovyIntentionsBundle.message("invert.if.intention.name"), null);

  @Test
  public void testDoNotTriggerOnIncompleteIf() {
    grIntention.get().doAntiTest("""
                 i<caret>f () {
                   succes
                 } else {
                   no_succes
                 }
                 """);
  }

  @Test
  public void testSimpleCondition() {
    grIntention.get().doTextTest("""
                 i<caret>f (a) {
                     succes
                 } else {
                     no_succes
                 }
                 """, """
                 i<caret>f (!a) {
                     no_succes
                 } else {
                     succes
                 }
                 """);
  }

  @Test
  public void testCallCondition() {

    grIntention.get().doTextTest("""
                 i<caret>f (func()) {
                     succes
                 } else {
                     no_succes
                 }
                 """, """
                 i<caret>f (!func()) {
                     no_succes
                 } else {
                     succes
                 }
                 """);
  }

  @Test
  public void testComplexCondition() {
    grIntention.get().doTextTest("""
                 i<caret>f (a && b) {
                     succes
                 } else {
                     no_succes
                 }
                 """, """
                 i<caret>f (!(a && b)) {
                     no_succes
                 } else {
                     succes
                 }
                 """);
  }

  @Test
  public void testNegatedComplexCondition() {
    grIntention.get().doTextTest("""
                 i<caret>f (!(a && b)) {
                     succes
                 } else {
                     no_succes
                 }
                 """, """
                 i<caret>f (a && b) {
                     no_succes
                 } else {
                     succes
                 }
                 """);
  }

  @Test
  public void testNegatedSimpleCondition() {
    grIntention.get().doTextTest("""
                 i<caret>f (!a) {
                     succes
                 } else {
                     no_succes
                 }
                 """, """
                 i<caret>f (a) {
                     no_succes
                 } else {
                     succes
                 }
                 """);
  }

  @Test
  public void testNoElseBlock() {
    grIntention.get().doTextTest("""
                 i<caret>f (a) {
                     succes
                 }
                 nosuccess
                 """, """
                 i<caret>f (!a) {
                 } else {
                     succes
                 }
                 nosuccess
                 """);
  }

  @Test
  public void testEmptyThenBlockIsRemoved() {
    grIntention.get().doTextTest("""
                 i<caret>f (a) {
                 } else {
                     no_succes
                 }
                 """, """
                 i<caret>f (!a) {
                     no_succes
                 }
                 """);
  }

  @Test
  public void testContinue() {
    grIntention.get().doTextTest("""
                 for (i in []) {
                     i<caret>f (2) {
                         print 2
                         continue
                     }
                 
                     print 3
                     print(3)
                 }
                 """, """
                 for (i in []) {
                     if (!(2)) {
                 
                         print 3
                         print(3)
                     } else {
                         print 2
                         continue
                     }
                 }
                 """);
  }
}
