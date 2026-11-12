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
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.intentions.GrIntentionFixtureKt.grIntentionFixture;

/**
 * @author Max Medvedev
 */
@TestApplication
public class Gr50SplitDeclarationTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_5_0);
  private final TestFixture<GrIntentionFixture> grIntention = grIntentionFixture(projectFixture, null, null);

  @Test
  public void testSingleVar() {
    grIntention.get().doTextTest("""
                 def abc = 5
                 """, GroovyIntentionsBundle.message("split.into.declaration.and.assignment"), """
                 def abc
                 abc = 5
                 """);
  }

  @Test
  public void testMultiVar() {
    grIntention.get().doTextTest("""
                 def abc = 5, cde = 7
                 """, GroovyIntentionsBundle.message("split.into.separate.declaration"), """
                 def abc = 5
                 def cde = 7
                 """);
  }

  @Test
  public void testTupleAssignment() {
    grIntention.get().doTextTest("""
                 def (abc, cde) = foo()
                 """, GroovyIntentionsBundle.message("split.into.declaration.and.assignment"), """
                 def (abc, cde)
                 (abc, cde) = foo()
                 """);
  }

  @Test
  public void testSimpleTupleAssignment() {
    grIntention.get().doTextTest("""
                 def (abc, cde) = [1, 2]
                 """, GroovyIntentionsBundle.message("split.into.separate.declaration"), """
                 def abc = 1
                 def cde = 2
                 """);
  }

  @Test
  public void testSimpleTupleAssignmentWithExplicitTypes() {
    grIntention.get().doTextTest("""
                 def (int abc, int cde) = [1, 2]
                 """, GroovyIntentionsBundle.message("split.into.separate.declaration"), """
                 int abc = 1
                 int cde = 2
                 """);
  }

  @Test
  public void testUnnamedVariable() {
    grIntention.get().doAntiTest("""
                 def (<caret>_, a) = [1, 2]
                 """, GroovyIntentionsBundle.message("split.into.separate.declaration"));
  }
}
