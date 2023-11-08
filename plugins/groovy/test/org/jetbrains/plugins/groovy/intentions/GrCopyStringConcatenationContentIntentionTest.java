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
import com.intellij.modcommand.ModCopyToClipboard;

public class GrCopyStringConcatenationContentIntentionTest extends GrIntentionTestCase{
  public void testCopyConcatenation() {
    myFixture.configureByText("Test.groovy", """
        def fn() {
          def str = "hello " <caret>+ "world";
        }
        """);
    IntentionAction intention = myFixture.findSingleIntention("Copy string concatenation text to clipboard");
    ModCommandAction action = intention.asModCommandAction();
    assertNotNull(action);
    ModCommand command = action.perform(myFixture.getActionContext());
    assertEquals(new ModCopyToClipboard("hello world"), command);
  }

  public void testCopyLiteral() {
    myFixture.configureByText("Test.groovy", """
        def fn() {
          def str = "hello <caret>world";
        }
        """);
    IntentionAction intention = myFixture.findSingleIntention("Copy string literal text to clipboard");
    ModCommandAction action = intention.asModCommandAction();
    assertNotNull(action);
    ModCommand command = action.perform(myFixture.getActionContext());
    assertEquals(new ModCopyToClipboard("hello world"), command);
  }
}
