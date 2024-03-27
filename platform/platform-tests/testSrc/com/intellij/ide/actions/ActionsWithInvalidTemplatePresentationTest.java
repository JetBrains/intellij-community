// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionStub;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.TextWithMnemonic;
import com.intellij.testFramework.junit5.DynamicTests;
import com.intellij.testFramework.junit5.NamedFailure;
import com.intellij.testFramework.junit5.TestApplication;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

@TestMethodOrder(MethodOrderer.MethodName.class)
@TestApplication
public class ActionsWithInvalidTemplatePresentationTest {
  private final Pattern ALLOWED_MNEMONICS = Pattern.compile("[0-9A-Za-z]");

  // TODO fix and remove the field
  private static final List<String> ACTIONS_TO_FIX = Arrays.asList(
    "InsertRubyInjection",
    "InsertRubyInjectionWithoutOutput"
  );

  @TestFactory
  public List<DynamicTest> testActionsPresentations() {
    ActionManagerImpl actionManager = (ActionManagerImpl)ActionManager.getInstance();

    List<NamedFailure> failures = new ArrayList<>();
    for (Iterator<AnAction> it = actionManager.actions(true).iterator(); it.hasNext(); ) {
      AnAction action = it.next();
      String id = actionManager.getId(action);
      if (ACTIONS_TO_FIX.contains(id)) {
        continue;
      }

      Presentation presentation = action.getTemplatePresentation();
      String text = presentation.getText();
      String description = presentation.getDescription();
      TextWithMnemonic fullText = presentation.getTextWithPossibleMnemonic().get();

      if (hasTemplates(text) || hasTemplates(description)) {
        failures.add(newFailure(action, id, text, description, "string template markup"));
      }

      if (hasHtmlTags(text) || hasHtmlTags(description)) {
        failures.add(newFailure(action, id, text, description, "HTML markup"));
      }

      if (StringUtil.isEmptyOrSpaces(text) && !StringUtil.isEmptyOrSpaces(description)) {
        failures.add(newFailure(action, id, text, description, "empty text but description is ok"));
      }

      if (fullText != null && fullText.hasMnemonic() &&
          !ALLOWED_MNEMONICS.matcher("" + fullText.getMnemonicChar()).matches()) {
        failures.add(newFailure(action, id, text, description, "invalid mnemonic: '" + fullText.getMnemonicChar() + "'"));
      }
    }
    return DynamicTests.asDynamicTests(failures, "incorrect project settings");
  }

  private static @NotNull NamedFailure newFailure(@NotNull AnAction action,
                                                  @NotNull String id,
                                                  @Nullable String text,
                                                  @Nullable String description,
                                                  @NotNull String comment) {
    Object aClass = action instanceof ActionStub ? "class " + ((ActionStub)action).getClassName() : action.getClass();
    String message = id + "; " + aClass + "; text: '" + text + "'; description: '" + description + "'";
    return new NamedFailure("Action '" + id + "': " + comment, message);
  }

  /**
   * Potential MessageFormat template
   */
  private static boolean hasTemplates(@Nullable String text) {
    return text != null && text.contains("{");
  }

  /**
   * Potential HTML
   */
  private static boolean hasHtmlTags(@Nullable String text) {
    return text != null && text.contains("<");
  }
}
