/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionStub;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class ActionsWithInvalidTemplatePresentationTest extends LightPlatformTestCase {
  private static final List<String> KNOWN_FALSE_POSITIVES = Arrays.asList(
    "InsertRubyInjection",
    "InsertRubyInjectionWithoutOutput",
    "ClassTemplateNavigation"
  );

  public void testActionsPresentations() {
    ActionManagerImpl mgr = (ActionManagerImpl)ActionManager.getInstance();

    Set<String> ids = mgr.getActionIds();
    List<String> failed = new ArrayList<>();

    for (String id : ids) {
      if (KNOWN_FALSE_POSITIVES.contains(id)) continue;

      AnAction action = mgr.getActionOrStub(id);
      if (action == null) fail("Can't find action: " + id);

      Presentation presentation = action.getTemplatePresentation();
      String text = presentation.getText();
      String description = presentation.getDescription();

      if (!isValidText(text) || !isValidText(description)) {
        Object aClass = action instanceof ActionStub ? "class "+((ActionStub)action).getClassName() : action.getClass();
        failed.add(id + "; " + aClass + "; text: '" + text + "'; description: '" + description + "'\n");
      }
    }
    System.err.println(failed);
    assertEmpty("The following actions might have invalid template presentation:\n", failed);
  }

  private static boolean isValidText(@Nullable String text) {
    if (text == null) return true;
    if (text.contains("{")) return false; // MessageFormat template
    if (text.contains("<")) return false; // HTML
    return true;
  }
}
