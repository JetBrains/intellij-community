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
 * @author Brice Dutheil
 * @author Hamlet D'Arcy
 */
@TestApplication
public class SplitIfTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GrIntentionFixture> grIntention = grIntentionFixture(projectFixture, null, null);

  @Test
  public void test_that_two_binary_operand_are_split_into_2_if_statements() {
    grIntention.get().doTextTest("""
                 if(a <caret>&& b) {
                   c();
                 }
                 """, "Split into 2 'if' statements", """
                 if (a) {
                     if (b) {
                         c();
                     }
                 }
                 """);
  }

  @Test
  public void test_that_two_binary_operand_are_not_split_when_if_statements_has_else_branch() {
    grIntention.get().doAntiTest("""
                 if(a <caret>&& b) {
                   c();
                 } else {
                   d();
                 }
                 """, "Split into 2 'if' statements");
  }
}
