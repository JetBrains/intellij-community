/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.intentions.GrIntentionFixtureKt.grIntentionFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class GrCopyStringConcatenationContentIntentionTest{
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GrIntentionFixture> grIntention = grIntentionFixture(projectFixture, null, null);

  @Test
  public void testCopyConcatenation() {
    LightJavaCodeInsightTestFixture myFixture = grIntention.get().getFixture();
    myFixture.configureByText("Test.groovy", """
        def fn() {
          def str = "hello " <caret>+ "world";
        }
        """);
    IntentionAction intention = myFixture.findSingleIntention("Copy string concatenation text to clipboard");
    ModCommandAction action = intention.asModCommandAction();
    assertNotNull(action);
    ModCommand command = ReadAction.computeBlocking(() -> action.perform(myFixture.getActionContext()));
    assertEquals(ModCommand.copyToClipboard("hello world"), command);
  }

  @Test
  public void testCopyLiteral() {
    LightJavaCodeInsightTestFixture myFixture = grIntention.get().getFixture();
    myFixture.configureByText("Test.groovy", """
        def fn() {
          def str = "hello <caret>world";
        }
        """);
    IntentionAction intention = myFixture.findSingleIntention("Copy string literal text to clipboard");
    ModCommandAction action = intention.asModCommandAction();
    assertNotNull(action);
    ModCommand command = ReadAction.computeBlocking(() -> action.perform(myFixture.getActionContext()));
    assertEquals(ModCommand.copyToClipboard("hello world"), command);
  }
}
