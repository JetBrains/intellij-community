/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.codeInsight.navigation.GotoImplementationHandler;
import com.intellij.codeInsight.navigation.GotoTargetHandler;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.codeInsight.template.impl.actions.ListTemplatesAction;
import com.intellij.ide.DataManager;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler;
import com.intellij.testFramework.TestDataFile;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.ComponentPopupBuilderImpl;
import com.intellij.ui.speedSearch.NameFilteringListModel;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.junit.Assert;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static junit.framework.Assert.assertTrue;

/**
 * @author Dmitry Avdeev
 */
public class CodeInsightTestUtil {
  private CodeInsightTestUtil() { }

  @Nullable
  public static IntentionAction findIntentionByText(@NotNull List<IntentionAction> actions, @NonNls @NotNull String text) {
    for (IntentionAction action : actions) {
      final String s = action.getText();
      if (s.equals(text)) {
        return action;
      }
    }
    return null;
  }

  @TestOnly
  public static void doIntentionTest(CodeInsightTestFixture fixture, @NonNls String file, @NonNls String actionText) {
    String extension = FileUtilRt.getExtension(file);
    file = FileUtil.getNameWithoutExtension(file);
    if (extension.isEmpty()) extension = "xml";
    doIntentionTest(fixture, actionText, file + "." + extension, file + "_after." + extension);
  }

  @TestOnly
  public static void doIntentionTest(@NotNull final CodeInsightTestFixture fixture, @NonNls final String action,
                                     @NotNull final String before, @NotNull final String after) {
    fixture.configureByFile(before);
    List<IntentionAction> availableIntentions = fixture.getAvailableIntentions();
    final IntentionAction intentionAction = findIntentionByText(availableIntentions, action);
    if (intentionAction == null) {
      Assert.fail("Action not found: " + action + " in place: " + fixture.getElementAtCaret() + " among " + availableIntentions);
    }
    fixture.launchAction(intentionAction);
    fixture.checkResultByFile(after, false);
  }

  public static void doWordSelectionTest(@NotNull final CodeInsightTestFixture fixture,
                                         @TestDataFile @NotNull final String before, @TestDataFile final String... after) {
    assert after != null && after.length > 0;
    fixture.configureByFile(before);

    final SelectWordHandler action = new SelectWordHandler(null);
    final DataContext dataContext = DataManager.getInstance().getDataContext(fixture.getEditor().getComponent());
    for (String file : after) {
      action.execute(fixture.getEditor(), dataContext);
      fixture.checkResultByFile(file, false);
    }
  }
  
  public static void doWordSelectionTestOnDirectory(@NotNull final CodeInsightTestFixture fixture,
                                                    @TestDataFile @NotNull final String directoryName,
                                                    @NotNull final String filesExtension) {
    final SelectWordHandler action = new SelectWordHandler(null);
    fixture.copyDirectoryToProject(directoryName, directoryName);
    fixture.configureByFile(directoryName + "/before." + filesExtension);
    int i = 1;
    while (true) {
      final String fileName = directoryName + "/after" + i + "." + filesExtension;
      if (new File(fixture.getTestDataPath() + "/" + fileName).exists()) {
        action.execute(fixture.getEditor(), DataManager.getInstance().getDataContext(fixture.getEditor().getComponent()));
        fixture.checkResultByFile(fileName);
        i++;
      }
      else {
        break;
      }
    }
    assertTrue("At least one 'after'-file required", i > 1);
  }

  public static void doSurroundWithTest(@NotNull final CodeInsightTestFixture fixture, @NotNull final Surrounder surrounder,
                                        @NotNull final String before, @NotNull final String after) {
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
                                        @NotNull final String before, @NotNull final String after) {
    fixture.configureByFile(before);
    new ListTemplatesAction().actionPerformedImpl(fixture.getProject(), fixture.getEditor());
    final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(fixture.getEditor());
    assert lookup != null;
    lookup.finishLookup(Lookup.NORMAL_SELECT_CHAR);
    fixture.checkResultByFile(after, false);
  }

  public static void doSmartEnterTest(@NotNull final CodeInsightTestFixture fixture,
                                      @NotNull final String before, @NotNull final String after) {
    fixture.configureByFile(before);
    final List<SmartEnterProcessor> processors = SmartEnterProcessors.INSTANCE.allForLanguage(fixture.getFile().getLanguage());
    new WriteCommandAction(fixture.getProject()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        final Editor editor = fixture.getEditor();
        for (SmartEnterProcessor processor : processors) {
          processor.process(getProject(), editor, fixture.getFile());
        }
      }
    }.execute();
    fixture.checkResultByFile(after, false);
  }

  public static void doFormattingTest(@NotNull final CodeInsightTestFixture fixture,
                                      @NotNull final String before, @NotNull final String after) {
    fixture.configureByFile(before);
    new WriteCommandAction(fixture.getProject()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        CodeStyleManager.getInstance(fixture.getProject()).reformat(fixture.getFile());
      }
    }.execute();
    fixture.checkResultByFile(after, false);
  }

  @TestOnly
  public static void doInlineRename(VariableInplaceRenameHandler handler, final String newName, CodeInsightTestFixture fixture) {
    doInlineRename(handler, newName, fixture.getEditor(), fixture.getElementAtCaret());
  }

  @TestOnly
  public static void doInlineRename(VariableInplaceRenameHandler handler, final String newName, @NotNull Editor editor, PsiElement elementAtCaret) {
    Project project = editor.getProject();
    TemplateManagerImpl templateManager = (TemplateManagerImpl)TemplateManager.getInstance(project);
    try {
      templateManager.setTemplateTesting(true);
      handler.doRename(elementAtCaret, editor, DataManager.getInstance().getDataContext(editor.getComponent()));
      if (editor instanceof EditorWindow) {
        editor = ((EditorWindow)editor).getDelegate();
      }
      TemplateState state = TemplateManagerImpl.getTemplateState(editor);
      assert state != null;
      final TextRange range = state.getCurrentVariableRange();
      assert range != null;
      final Editor finalEditor = editor;
      new WriteCommandAction.Simple(project) {
        @Override
        protected void run() throws Throwable {
          finalEditor.getDocument().replaceString(range.getStartOffset(), range.getEndOffset(), newName);
        }
      }.execute().throwException();

      state = TemplateManagerImpl.getTemplateState(editor);
      assert state != null;
      state.gotoEnd(false);
    }
    finally {
      templateManager.setTemplateTesting(false);
    }
  }

  @TestOnly
  public static void doInlineRenameTest(VariableInplaceRenameHandler handler, String file, String extension,
                                        String newName, CodeInsightTestFixture fixture) {
    fixture.configureByFile(file + "." + extension);
    doInlineRename(handler, newName, fixture);
    fixture.checkResultByFile(file + "_after." + extension);
  }

  public static void doActionTest(AnAction action, String file, CodeInsightTestFixture fixture) {
    String extension = FileUtilRt.getExtension(file);
    String name = FileUtil.getNameWithoutExtension(file);
    fixture.configureByFile(file);
    fixture.testAction(action);
    fixture.checkResultByFile(name + "_after." + extension);
  }

  public static void addTemplate(final Template template, Disposable parentDisposable) {
    final TemplateSettings settings = TemplateSettings.getInstance();
    settings.addTemplate(template);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        settings.removeTemplate(template);
      }
    });
  }

  @NotNull
  @TestOnly
  public static GotoTargetHandler.GotoData gotoImplementation(Editor editor, PsiFile file) {
    GotoTargetHandler.GotoData data = new GotoImplementationHandler().getSourceAndTargetElements(editor, file);
    if (data.listUpdaterTask != null) {
      JBList list = new JBList();
      CollectionListModel model = new CollectionListModel(new ArrayList());
      list.setModel(model);
      list.setModel(new NameFilteringListModel(list, Function.ID, Condition.FALSE, String::new));
      JBPopup popup = new ComponentPopupBuilderImpl(list, null).createPopup();
      data.listUpdaterTask.init((AbstractPopup)popup, list, new Ref<>());

      data.listUpdaterTask.queue();

      try {
        while (!data.listUpdaterTask.isFinished()) {
          UIUtil.dispatchAllInvocationEvents();
        }
      }
      finally {
        Disposer.dispose(popup);
      }
    }
    return data;
  }
}
