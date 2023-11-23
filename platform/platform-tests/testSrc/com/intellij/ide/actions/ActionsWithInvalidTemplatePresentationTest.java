// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import java.util.Iterator;
import java.util.List;

public class ActionsWithInvalidTemplatePresentationTest extends LightPlatformTestCase {
  private static final List<String> KNOWN_FALSE_POSITIVES = Arrays.asList(
    "InsertRubyInjection",
    "InsertRubyInjectionWithoutOutput"
  );

  public void testActionsPresentations() {
    ActionManagerImpl actionManager = (ActionManagerImpl)ActionManager.getInstance();

    List<String> failed = new ArrayList<>();
    for (Iterator<AnAction> it = actionManager.actions(true).iterator(); it.hasNext(); ) {
      AnAction action = it.next();
      String id = actionManager.getId(action);
      if (KNOWN_FALSE_POSITIVES.contains(id)) {
        continue;
      }

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
