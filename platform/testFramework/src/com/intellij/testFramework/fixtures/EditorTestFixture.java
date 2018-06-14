// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionProgressIndicator;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.internal.DumpLookupElementWeights;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl.instantiateAndRun;
import static org.junit.Assert.*;

/**
 * @author yole
 */
public class EditorTestFixture {
  private final Project myProject;
  private final Editor myEditor;
  private final VirtualFile myFile;

  private boolean myEmptyLookup;

  public EditorTestFixture(Project project, Editor editor, VirtualFile file) {
    myProject = project;
    myEditor = editor;
    myFile = file;
  }

  public void type(char c) {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      final EditorActionManager actionManager = EditorActionManager.getInstance();
      if (c == '\b') {
        performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE);
        return;
      }
      if (c == '\n') {
        if (performEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM)) {
          return;
        }
        if (performEditorAction(IdeActions.ACTION_EDITOR_NEXT_TEMPLATE_VARIABLE)) {
          return;
        }

        performEditorAction(IdeActions.ACTION_EDITOR_ENTER);
        return;
      }
      if (c == '\t') {
        if (performEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_REPLACE)) {
          return;
        }
        if (performEditorAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB)) {
          return;
        }
        if (performEditorAction(IdeActions.ACTION_EDITOR_NEXT_TEMPLATE_VARIABLE)) {
          return;
        }
        if (performEditorAction(IdeActions.ACTION_EDITOR_TAB)) {
          return;
        }
      }
      if (c == Lookup.COMPLETE_STATEMENT_SELECT_CHAR) {
        if (performEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_COMPLETE_STATEMENT)) {
          return;
        }
      }

      ActionManagerEx.getInstanceEx().fireBeforeEditorTyping(c, getEditorDataContext());
      actionManager.getTypedAction().actionPerformed(myEditor, c, getEditorDataContext());
    });

  }

  public void type(@NotNull String s) {
    for (int i = 0; i < s.length(); i++) {
      type(s.charAt(i));
    }
  }

  public boolean performEditorAction(@NotNull String actionId) {
    final DataContext dataContext = getEditorDataContext();

    final ActionManagerEx managerEx = ActionManagerEx.getInstanceEx();
    final AnAction action = managerEx.getAction(actionId);
    final AnActionEvent event = new AnActionEvent(null, dataContext, ActionPlaces.UNKNOWN, new Presentation(), managerEx, 0);

    action.beforeActionPerformedUpdate(event);

    if (!event.getPresentation().isEnabled()) {
      return false;
    }

    managerEx.fireBeforeActionPerformed(action, dataContext, event);

    ActionUtil.performActionDumbAware(action, event);

    managerEx.fireAfterActionPerformed(action, dataContext, event);
    return true;
  }

  @NotNull
  private DataContext getEditorDataContext() {
    return ((EditorEx)myEditor).getDataContext();
  }

  public PsiFile getFile() {
    return myFile != null ? ReadAction.compute(() -> PsiManager.getInstance(myProject).findFile(myFile)) : null;
  }

  public List<HighlightInfo> doHighlighting() {
    return doHighlighting(false);
  }

  public List<HighlightInfo> doHighlighting(boolean myAllowDirt) {
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(myProject).commitAllDocuments());

    PsiFile file = getFile();
    Editor editor = myEditor;
    if (editor instanceof EditorWindow) {
      editor = ((EditorWindow)editor).getDelegate();
      file = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
    }
    assertNotNull(file);
    return instantiateAndRun(file, editor, ArrayUtil.EMPTY_INT_ARRAY, myAllowDirt);
  }

  @Nullable
  protected Editor getCompletionEditor() {
    return InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(myEditor, getFile());
  }

  public LookupImpl getLookup() {
    return (LookupImpl)LookupManager.getActiveLookup(myEditor);
  }

  public LookupElement[] complete(@NotNull final CompletionType type) {
    return complete(type, 1);
  }

  public LookupElement[] completeBasic() {
    return complete(CompletionType.BASIC);
  }

  public LookupElement[] complete(@NotNull final CompletionType type, final int invocationCount) {
    myEmptyLookup = false;
    ApplicationManager.getApplication().invokeAndWait(() -> CommandProcessor.getInstance().executeCommand(myProject, () -> {
      final CodeCompletionHandlerBase handler = new CodeCompletionHandlerBase(type) {
        @Override
        @SuppressWarnings("deprecation")
        protected void completionFinished(CompletionProgressIndicator indicator, boolean hasModifiers) {
          myEmptyLookup = indicator.getLookup().getItems().isEmpty();
          super.completionFinished(indicator, hasModifiers);
        }
      };
      Editor editor = getCompletionEditor();
      assertNotNull(editor);
      handler.invokeCompletion(myProject, editor, invocationCount);
      PsiDocumentManager.getInstance(myProject).commitAllDocuments(); // to compare with file text
    }, null, null, myEditor.getDocument()));
    return getLookupElements();
  }

  @Nullable
  public LookupElement[] getLookupElements() {
    LookupImpl lookup = getLookup();
    if (lookup == null) {
      return myEmptyLookup ? LookupElement.EMPTY_ARRAY : null;
    }
    else {
      final List<LookupElement> list = lookup.getItems();
      return list.toArray(LookupElement.EMPTY_ARRAY);
    }
  }

  public List<String> getLookupElementStrings() {
    final LookupElement[] elements = getLookupElements();
    if (elements == null) return null;

    return ContainerUtil.map(elements, LookupElement::getLookupString);
  }

  @NotNull
  public final List<LookupElement> completeBasicAllCarets(@Nullable final Character charToTypeAfterCompletion) {
    final CaretModel caretModel = myEditor.getCaretModel();
    final List<Caret> carets = caretModel.getAllCarets();

    final List<Integer> originalOffsets = new ArrayList<>(carets.size());

    for (final Caret caret : carets) {
      originalOffsets.add(caret.getOffset());
    }
    caretModel.removeSecondaryCarets();

    // We do it in reverse order because completions would affect offsets
    // i.e.: when you complete "spa" to "spam", next caret offset increased by 1
    Collections.reverse(originalOffsets);
    final List<LookupElement> result = new ArrayList<>();
    for (final int originalOffset : originalOffsets) {
      caretModel.moveToOffset(originalOffset);
      final LookupElement[] lookupElements = completeBasic();
      if (charToTypeAfterCompletion != null) {
        type(charToTypeAfterCompletion);
      }
      if (lookupElements != null) {
        result.addAll(Arrays.asList(lookupElements));
      }
    }
    return result;
  }

  public void assertPreferredCompletionItems(final int selected, @NotNull final String... expected) {
    final LookupImpl lookup = getLookup();
    assertNotNull("No lookup is shown", lookup);

    final JList list = lookup.getList();
    List<String> strings = getLookupElementStrings();
    assertNotNull(strings);
    final List<String> actual = strings.subList(0, Math.min(expected.length, strings.size()));
    if (!actual.equals(Arrays.asList(expected))) {
      UsefulTestCase.assertOrderedEquals(DumpLookupElementWeights.getLookupElementWeights(lookup, false), expected);
    }
    if (selected != list.getSelectedIndex()) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println(DumpLookupElementWeights.getLookupElementWeights(lookup, false));
    }
    assertEquals(selected, list.getSelectedIndex());
  }

  public void finishLookup(char completionChar) {
    Runnable command = () -> {
      LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myEditor);
      assertNotNull(lookup);
      lookup.finishLookup(completionChar);
    };
    CommandProcessor.getInstance().executeCommand(myProject, command, null, null);
  }

  public PsiElement getElementAtCaret() {
    Editor editor = getCompletionEditor();
    int findTargetFlags = TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED | TargetElementUtil.ELEMENT_NAME_ACCEPTED;
    PsiElement element = TargetElementUtil.findTargetElement(editor, findTargetFlags);

    // if no references found in injected fragment, try outer document
    if (element == null && editor instanceof EditorWindow) {
      element = TargetElementUtil.findTargetElement(((EditorWindow)editor).getDelegate(), findTargetFlags);
    }

    if (element == null) {
      fail("element not found in file " + myFile.getName() +
           " at caret position offset " + myEditor.getCaretModel().getOffset() + "," +
           " psi structure:\n" + DebugUtil.psiToString(getFile(), true, true));
    }
    return element;
  }

  public <T extends PsiElement> T findElementByText(@NotNull String text, @NotNull Class<T> elementClass) {
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(getFile());
    assertNotNull(document);
    int pos = document.getText().indexOf(text);
    assertTrue(text, pos >= 0);
    return PsiTreeUtil.getParentOfType(getFile().findElementAt(pos), elementClass);
  }
}
