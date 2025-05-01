// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.breadcrumbs.FileBreadcrumbsCollector;
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionProgressIndicator;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.ide.IdeEventQueue;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.internal.DumpLookupElementWeights;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.impl.ActionProcessor;
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
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
import com.intellij.ui.ClientProperty;
import com.intellij.ui.components.breadcrumbs.Crumb;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;

import static com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl.instantiateAndRun;
import static org.junit.Assert.*;


public class EditorTestFixture {
  private static final @NotNull Logger LOG = Logger.getInstance(EditorTestFixture.class);

  private final @NotNull Project myProject;
  private final @NotNull Editor myEditor;
  private final @NotNull VirtualFile myVirtualFile;

  private boolean myEmptyLookup;

  public EditorTestFixture(@NotNull Project project, @NotNull Editor editor, @NotNull VirtualFile file) {
    myProject = project;
    myEditor = editor;
    myVirtualFile = file;
  }

  public void type(char c) {
    if (ProgressIndicatorUtils.isWriteActionRunningOrPending(ApplicationManagerEx.getApplicationEx())) {
      // TODO make LOG.error
      LOG.warn("type() must not be in WA");
    }
    ApplicationManager.getApplication().invokeAndWait(() -> {
      int keyCode = KeyEvent.getExtendedKeyCodeForChar(c);
      KeyEvent keyEvent = new KeyEvent(getEditor().getContentComponent(), KeyEvent.KEY_PRESSED, -1, 0, keyCode, c);
      if (!Character.isLetterOrDigit(keyEvent.getKeyChar()) || ClientProperty.get(
        getEditor().getContentComponent(), ActionUtil.ALLOW_PlAIN_LETTER_SHORTCUTS) == Boolean.TRUE) {
        KeyboardShortcut shortcut =
          c == Lookup.COMPLETE_STATEMENT_SELECT_CHAR ?
          (KeyboardShortcut)Objects.requireNonNull(KeymapUtil.getPrimaryShortcut("EditorCompleteStatement")) :
          new KeyboardShortcut(KeyStroke.getKeyStroke(keyCode, 0), null);
        IdeKeyEventDispatcher keyEventDispatcher = IdeEventQueue.getInstance().getKeyEventDispatcher();
        keyEventDispatcher.updateCurrentContext(getEditor().getContentComponent(), shortcut);
        keyEventDispatcher.getContext().setProject(myProject);
        keyEventDispatcher.getContext().setDataContext(getEditorDataContext());
        keyEventDispatcher.getContext().setShortcut(shortcut);
        try {
          if (keyEventDispatcher.processAction(keyEvent, new ActionProcessor() {
            @Override
            public void performAction(@NotNull InputEvent inputEvent, @NotNull AnAction action, @NotNull AnActionEvent event) {
              super.performAction(inputEvent, action, event);
              LOG.info("type(): performing action '" + event.getActionManager().getId(action) + "'");
            }
          })) {
            return;
          }
        }
        finally {
          keyEventDispatcher.getContext().clear();
        }
      }
      ActionManagerEx.getInstanceEx().fireBeforeEditorTyping(c, getEditorDataContext());
      TypedAction.getInstance().actionPerformed(myEditor, c, getEditorDataContext());
      ActionManagerEx.getInstanceEx().fireAfterEditorTyping(c, getEditorDataContext());
    });
  }

  public void type(@NotNull String s) {
    for (int i = 0; i < s.length(); i++) {
      type(s.charAt(i));
    }
  }

  public boolean performEditorAction(@NotNull String actionId) {
    return performEditorAction(actionId, null);
  }

  public boolean performEditorAction(@NotNull String actionId, @Nullable AnActionEvent actionEvent) {
    ActionManagerEx managerEx = ActionManagerEx.getInstanceEx();
    AnAction action = managerEx.getAction(actionId);
    AnActionEvent event =
      actionEvent != null ? actionEvent :
      new AnActionEvent(null, getEditorDataContext(), ActionPlaces.UNKNOWN, new Presentation(), managerEx, 0);
    PerformWithDocumentsCommitted.commitDocumentsIfNeeded(action, event);
    ActionUtil.updateAction(action, event);
    if (event.getPresentation().isEnabled()) {
      ActionUtil.performAction(action, event);
      LOG.info("performEditorAction(): performing action '" + event.getActionManager().getId(action) + "'");
      return true;
    }
    return false;
  }

  private @NotNull DataContext getEditorDataContext() {
    return EditorUtil.getEditorDataContext(myEditor);
  }

  public PsiFile getFile() {
    return ReadAction.compute(() -> PsiManager.getInstance(myProject).findFile(myVirtualFile));
  }

  public @NotNull @Unmodifiable List<HighlightInfo> doHighlighting() {
    return doHighlighting(false, false);
  }

  public @NotNull @Unmodifiable List<HighlightInfo> doHighlighting(boolean myAllowDirt, boolean readEditorMarkupModel) {
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(myProject).commitAllDocuments());

    PsiFile file = getFile();
    Editor editor = myEditor;
    if (editor instanceof EditorWindow) {
      editor = ((EditorWindow)editor).getDelegate();
      file = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
    }
    assertNotNull(file);
    return instantiateAndRun(file, editor, ArrayUtilRt.EMPTY_INT_ARRAY, myAllowDirt, readEditorMarkupModel);
  }

  protected @NotNull Editor getCompletionEditor() {
    return InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(myEditor, getFile());
  }

  public LookupImpl getLookup() {
    return (LookupImpl)LookupManager.getActiveLookup(myEditor);
  }

  public LookupElement[] complete(final @NotNull CompletionType type) {
    return complete(type, 1);
  }

  public LookupElement[] completeBasic() {
    return complete(CompletionType.BASIC);
  }

  public LookupElement[] complete(final @NotNull CompletionType type, final int invocationCount) {
    myEmptyLookup = false;
    ApplicationManager.getApplication().invokeAndWait(() -> CommandProcessor.getInstance().executeCommand(myProject, () -> {
      final CodeCompletionHandlerBase handler = new CodeCompletionHandlerBase(type) {
        @Override
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

  public LookupElement @Nullable [] getLookupElements() {
    LookupImpl lookup = getLookup();
    if (lookup == null) {
      return myEmptyLookup ? LookupElement.EMPTY_ARRAY : null;
    }
    else {
      final List<LookupElement> list = lookup.getItems();
      return list.toArray(LookupElement.EMPTY_ARRAY);
    }
  }

  public @Unmodifiable List<String> getLookupElementStrings() {
    final LookupElement[] elements = getLookupElements();
    if (elements == null) return null;

    return ContainerUtil.map(elements, LookupElement::getLookupString);
  }

  public final @NotNull List<LookupElement> completeBasicAllCarets(final @Nullable Character charToTypeIfOnlyOneOrNoCompletion,
                                                                   final @Nullable Character charToTypeIfMultipleCompletions) {
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
      if ((lookupElements == null || lookupElements.length == 0) && charToTypeIfOnlyOneOrNoCompletion != null) {
        type(charToTypeIfOnlyOneOrNoCompletion);
      } else if (lookupElements != null && lookupElements.length > 0 && charToTypeIfMultipleCompletions != null) {
        type(charToTypeIfMultipleCompletions);
      }
      if (lookupElements != null) {
        result.addAll(Arrays.asList(lookupElements));
      }
    }
    return result;
  }

  public void assertPreferredCompletionItems(final int selected, final String @NotNull ... expected) {
    final LookupImpl lookup = getLookup();
    assertNotNull("No lookup is shown", lookup);

    final JList<LookupElement> list = lookup.getList();
    List<String> strings = getLookupElementStrings();
    assertNotNull(strings);
    final List<String> actual = ContainerUtil.getFirstItems(strings, expected.length);
    if (!actual.equals(Arrays.asList(expected))) {
      UsefulTestCase.assertOrderedEquals(DumpLookupElementWeights.getLookupElementWeights(lookup, false), expected);
    }
    if (selected != list.getSelectedIndex()) {
      //noinspection UseOfSystemOutOrSystemErr
      DumpLookupElementWeights.getLookupElementWeights(lookup, false).forEach(System.out::println);
    }
    assertEquals(selected, list.getSelectedIndex());
  }

  public void finishLookup(char completionChar) {
    Runnable command = () -> {
      LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myEditor);
      assertNotNull(lookup);
      lookup.finishLookup(completionChar);
    };
    CommandProcessor.getInstance().executeCommand(myProject, command, null, null, myEditor.getDocument());
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
      PsiFile psiFile = getFile();
      int offset = myEditor.getCaretModel().getOffset();
      int expectedCaretOffset = editor instanceof EditorEx ? ((EditorEx)editor).getExpectedCaretOffset() : offset;
      fail("element not found in file " + psiFile + "(" + psiFile.getClass() + ", " + psiFile.getViewProvider() + ", " + editor.getProject() + ")" +
           " at caret position offset " + offset + (offset == expectedCaretOffset ? "" : ", expected caret offset: "+expectedCaretOffset) +
           ", psi structure:\n" + DebugUtil.psiToString(psiFile, true, true) +
           ", elementAt(" + offset + ")=" + psiFile.findElementAt(offset) +
           ", editor=" + editor +
           ", adjusted offset=" + TargetElementUtilBase.adjustOffset(psiFile, editor.getDocument(), offset)+
           ", TargetElementUtilBase.findTargetElement(editor, flags, offset)=" + TargetElementUtilBase.findTargetElement(editor, findTargetFlags, offset)
      );
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

  public @NotNull List<IntentionAction> getAllQuickFixes() {
    List<HighlightInfo> infos = doHighlighting();
    List<IntentionAction> actions = new ArrayList<>();
    for (HighlightInfo info : infos) {
      info.findRegisteredQuickFix((descriptor, range) -> {
        actions.add(descriptor.getAction());
        return null;
      });
    }
    return actions;
  }

  public @NotNull @Unmodifiable List<Crumb> getBreadcrumbsAtCaret() {
    FileBreadcrumbsCollector breadcrumbsCollector = FileBreadcrumbsCollector.findBreadcrumbsCollector(myProject, myVirtualFile);
    return ContainerUtil.newArrayList(breadcrumbsCollector.computeCrumbs(myVirtualFile, myEditor.getDocument(), myEditor.getCaretModel().getOffset(), true));
  }

  public @NotNull Editor getEditor() {
    return myEditor;
  }
}
