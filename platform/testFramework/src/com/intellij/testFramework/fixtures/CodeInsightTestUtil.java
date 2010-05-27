/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.testFramework.fixtures;

import com.intellij.codeInsight.editorActions.SelectWordHandler;
import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessor;
import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessors;
import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.impl.actions.ListTemplatesAction;
import com.intellij.ide.DataManager;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class CodeInsightTestUtil {
  
  private CodeInsightTestUtil() {
  }

  @Nullable
  public static IntentionAction findIntentionByText(List<IntentionAction> actions, @NonNls String text) {
    for (IntentionAction action : actions) {
      final String s = action.getText();
      if (s.equals(text)) {
        return action;
      }
    }
    return null;
  }

  public static void doIntentionTest(CodeInsightTestFixture fixture, @NonNls String file, @NonNls String actionText) throws Throwable {
    doIntentionTest(fixture, actionText, file + ".xml", file + "_after.xml");
  }

  public static void doIntentionTest(@NotNull final CodeInsightTestFixture fixture, @NonNls final String action,
                                     @NotNull final String before, @NotNull final String after) throws Exception {
    fixture.configureByFile(before);
    final IntentionAction intentionAction = findIntentionByText(fixture.getAvailableIntentions(), action);
    assert intentionAction != null : "Action not found: " + action;
    new WriteCommandAction(fixture.getProject()) {
      protected void run(Result result) throws Throwable {
        fixture.launchAction(intentionAction);
      }
    }.execute();
    fixture.checkResultByFile(after, false);
  }

  public static void doWordSelectionTest(@NotNull final CodeInsightTestFixture fixture,
                                         @NotNull final String before, final String... after) throws Exception {
    assert after != null && after.length > 0;
    fixture.configureByFile(before);

    final SelectWordHandler action = new SelectWordHandler(null);
    final DataContext dataContext = DataManager.getInstance().getDataContext(fixture.getEditor().getComponent());
    for (String file : after) {
      action.execute(fixture.getEditor(), dataContext);
      fixture.checkResultByFile(file, false);
    }
  }

  public static void doSurroundWithTest(@NotNull final CodeInsightTestFixture fixture, @NotNull final Surrounder surrounder,
                                        @NotNull final String before, @NotNull final String after) throws Exception {
    fixture.configureByFile(before);
    new WriteCommandAction.Simple(fixture.getProject()) {
      @Override
      protected void run() throws Throwable {
        SurroundWithHandler.invoke(fixture.getProject(), fixture.getEditor(), fixture.getFile(), surrounder);
      }
    }.execute();
    fixture.checkResultByFile(after, false);
  }

  public static void doLiveTemplateTest(@NotNull final CodeInsightTestFixture fixture,
                                        @NotNull final String before, @NotNull final String after) throws Exception {
    fixture.configureByFile(before);
    new WriteCommandAction(fixture.getProject()) {
      protected void run(Result result) throws Throwable {
        new ListTemplatesAction().actionPerformedImpl(fixture.getProject(), fixture.getEditor());
        final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(fixture.getEditor());
        assert lookup != null;
        lookup.finishLookup(Lookup.NORMAL_SELECT_CHAR);
      }
    }.execute();
    fixture.checkResultByFile(after, false);
  }

  public static void doSmartEnterTest(@NotNull final CodeInsightTestFixture fixture,
                                      @NotNull final String before, @NotNull final String after) throws Exception {
    fixture.configureByFile(before);
    final List<SmartEnterProcessor> processors = SmartEnterProcessors.INSTANCE.forKey(fixture.getFile().getLanguage());
    new WriteCommandAction(fixture.getProject()) {
      protected void run(Result result) throws Throwable {
        final Editor editor = fixture.getEditor();
        for (SmartEnterProcessor processor : processors) {
          processor.process(getProject(), editor, fixture.getFile());
        }
      }
    }.execute();
    fixture.checkResultByFile(after, false);
  }

  public static void doFormattingTest(@NotNull final CodeInsightTestFixture fixture,
                                      @NotNull final String before, @NotNull final String after) throws Exception {
    fixture.configureByFile(before);
    new WriteCommandAction(fixture.getProject()) {
      protected void run(Result result) throws Throwable {
        CodeStyleManager.getInstance(fixture.getProject()).reformat(fixture.getFile());
      }
    }.execute();
    fixture.checkResultByFile(after, false);
  }

}
