// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures;

import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessor;
import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessors;
import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.multiverse.EditorContextManager;
import com.intellij.codeInsight.multiverse.FileViewProviderUtil;
import com.intellij.codeInsight.navigation.GotoImplementationHandler;
import com.intellij.codeInsight.navigation.GotoTargetHandler;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.codeInsight.template.impl.actions.ListTemplatesAction;
import com.intellij.ide.DataManager;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBListUpdater;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiEditorUtil;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.inplace.InplaceRefactoring;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.TestDataFile;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.ComponentPopupBuilderImpl;
import com.intellij.ui.speedSearch.NameFilteringListModel;
import com.intellij.util.Functions;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.junit.Assert;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import static junit.framework.Assert.assertTrue;

/**
 * @author Dmitry Avdeev
 */
public final class CodeInsightTestUtil {
  private CodeInsightTestUtil() { }

  public static @Nullable IntentionAction findIntentionByText(@NotNull List<? extends IntentionAction> actions, @NonNls @NotNull String text) {
    for (IntentionAction action : actions) {
      final String s = action.getText();
      if (s.equals(text)) {
        return action;
      }
    }
    return null;
  }

  public static @Nullable IntentionAction findIntentionByPartialText(@NotNull List<? extends IntentionAction> actions, @NonNls @NotNull String text) {
    for (IntentionAction action : actions) {
      final String s = action.getText();
      if (s.contains(text)) {
        return action;
      }
    }
    return null;
  }

  @TestOnly
  public static void doIntentionTest(CodeInsightTestFixture fixture, @NonNls String file, @NonNls String actionText) {
    String extension = FileUtilRt.getExtension(file);
    file = FileUtilRt.getNameWithoutExtension(file);
    if (extension.isEmpty()) extension = "xml";
    doIntentionTest(fixture, actionText, file + "." + extension, file + "_after." + extension);
  }

  @TestOnly
  public static void doIntentionTest(final @NotNull CodeInsightTestFixture fixture, final @NonNls String action,
                                     final @NotNull String before, final @NotNull String after) {
    fixture.configureByFile(before);
    List<IntentionAction> availableIntentions = fixture.getAvailableIntentions();
    final IntentionAction intentionAction = findIntentionByText(availableIntentions, action);
    if (intentionAction == null) {
      PsiElement element = fixture.getFile().findElementAt(fixture.getCaretOffset());
      Assert.fail("Action not found: " + action + " in place: " + element + " among " + availableIntentions);
    }
    fixture.launchAction(intentionAction);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    fixture.checkResultByFile(after, false);
  }

  public static void doWordSelectionTest(final @NotNull CodeInsightTestFixture fixture,
                                         @TestDataFile final @NotNull String before, @TestDataFile final String... after) {
    EdtTestUtil.runInEdtAndWait(() -> {
      assert after != null && after.length > 0;
      fixture.configureByFile(before);

      for (String file : after) {
        fixture.performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
        fixture.checkResultByFile(file, false);
      }
    });
  }

  public static void doWordSelectionTestOnDirectory(final @NotNull CodeInsightTestFixture fixture,
                                                    @TestDataFile final @NotNull String directoryName,
                                                    final @NotNull String filesExtension) {
    EdtTestUtil.runInEdtAndWait(() -> {
      fixture.copyDirectoryToProject(directoryName, directoryName);
      fixture.configureByFile(directoryName + "/before." + filesExtension);
      int i = 1;
      while (true) {
        final String fileName = directoryName + "/after" + i + "." + filesExtension;
        if (new File(fixture.getTestDataPath() + "/" + fileName).exists()) {
          fixture.performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
          fixture.checkResultByFile(fileName);
          i++;
        }
        else {
          break;
        }
      }
      assertTrue("At least one 'after'-file required", i > 1);
    });
  }

  public static void doSurroundWithTest(final @NotNull CodeInsightTestFixture fixture, final @NotNull Surrounder surrounder,
                                        final @NotNull String before, final @NotNull String after) {
    fixture.configureByFile(before);
    WriteCommandAction.writeCommandAction(fixture.getProject())
                      .run(() -> SurroundWithHandler.invoke(fixture.getProject(), fixture.getEditor(), fixture.getFile(), surrounder));
    fixture.checkResultByFile(after, false);
  }

  public static void doLiveTemplateTest(final @NotNull CodeInsightTestFixture fixture,
                                        final @NotNull String before, final @NotNull String after) {
    fixture.configureByFile(before);
    new ListTemplatesAction().actionPerformedImpl(fixture.getProject(), fixture.getEditor());
    final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(fixture.getEditor());
    assert lookup != null;
    lookup.finishLookup(Lookup.NORMAL_SELECT_CHAR);
    fixture.checkResultByFile(after, false);
  }

  public static void doSmartEnterTest(final @NotNull CodeInsightTestFixture fixture,
                                      final @NotNull String before, final @NotNull String after) {
    fixture.configureByFile(before);
    final List<SmartEnterProcessor> processors = SmartEnterProcessors.INSTANCE.allForLanguage(fixture.getFile().getLanguage());
    WriteCommandAction.writeCommandAction(fixture.getProject()).run(() -> {
      final Editor editor = fixture.getEditor();
      for (SmartEnterProcessor processor : processors) {
        processor.process(fixture.getProject(), editor, fixture.getFile());
      }
    });
    fixture.checkResultByFile(after, false);
  }

  public static void doFormattingTest(final @NotNull CodeInsightTestFixture fixture,
                                      final @NotNull String before, final @NotNull String after) {
    fixture.configureByFile(before);
    WriteCommandAction.writeCommandAction(fixture.getProject()).run(() -> CodeStyleManager.getInstance(fixture.getProject()).reformat(fixture.getFile()));
    fixture.checkResultByFile(after, false);
  }

  public static void doInlineRename(VariableInplaceRenameHandler handler, final String newName, CodeInsightTestFixture fixture) {
    PsiElement elementAtCaret = fixture.getElementAtCaret();
    Editor editorForElement = openEditorFor(elementAtCaret);
    doInlineRename(handler, newName, editorForElement, elementAtCaret);
  }

  public static @NotNull Editor openEditorFor(@NotNull PsiElement elementAtCaret) {
    // sometimes the element found by TargetElementUtil may belong to the other editor, e.g, in case of an injected element
    // but inplace rename requires that both element and the editor must be consistent
    Editor editorForElement = PsiEditorUtil.getInstance().findEditorByPsiElement(elementAtCaret);
    if (editorForElement == null) {
      PsiFile containingFile = elementAtCaret.getContainingFile();
      VirtualFile virtualFile = containingFile.getVirtualFile();
      Project project = containingFile.getProject();
      editorForElement = FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, virtualFile), false);
      editorForElement.getCaretModel().moveToOffset(elementAtCaret.getTextOffset());
    }
    return editorForElement;
  }

  public static void doInlineRename(VariableInplaceRenameHandler handler, final String newName, @NotNull Editor editor, PsiElement elementAtCaret) {
    if (!tryInlineRename(handler, newName, editor, elementAtCaret)) {
      Assert.fail("Inline refactoring wasn't performed");
    }
  }

  /**
   * @return true if the refactoring was performed, false otherwise
   */
  @TestOnly
  public static boolean tryInlineRename(VariableInplaceRenameHandler handler,
                                        final String newName,
                                        @NotNull Editor editor,
                                        @NotNull PsiElement elementAtCaret) {
    Project project = editor.getProject();
    Disposable disposable = Disposer.newDisposable();
    try {
      TemplateManagerImpl.setTemplateTesting(disposable);
      DataContext context = DataManager.getInstance().getDataContext(editor.getComponent());
      InplaceRefactoring renamer =
        handler.doRename(Objects.requireNonNullElse(PsiElementRenameHandler.getElement(context), elementAtCaret), editor, context);
      if (editor instanceof EditorWindow) {
        editor = ((EditorWindow)editor).getDelegate();
      }
      TemplateState state = TemplateManagerImpl.getTemplateState(editor);
      if (state == null) {
        if (renamer != null) {
          renamer.finish(false);
        }
        return false;
      }
      final TextRange range = state.getCurrentVariableRange();
      assert range != null;
      final Editor finalEditor = editor;
      WriteCommandAction.writeCommandAction(project)
                        .run(() -> finalEditor.getDocument().replaceString(range.getStartOffset(), range.getEndOffset(), newName));

      state = TemplateManagerImpl.getTemplateState(editor);
      assert state != null;
      state.gotoEnd(false);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    }
    finally {
      Disposer.dispose(disposable);
    }
    return true;
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
    String name = FileUtilRt.getNameWithoutExtension(file);
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

  @TestOnly
  public static @NotNull GotoTargetHandler.GotoData gotoImplementation(Editor editor, PsiFile file) {
    GotoTargetHandler.GotoData data = new GotoImplementationHandler().getSourceAndTargetElements(editor, file);
    data.initPresentations();
    if (data.listUpdaterTask != null) {
      JBList<Object> list = new JBList<>();
      CollectionListModel<Object> model = new CollectionListModel<>(new ArrayList<>());
      list.setModel(new NameFilteringListModel<>(model, Functions.identity(), Conditions.alwaysFalse(), String::new));
      JBPopup popup = new ComponentPopupBuilderImpl(list, null).createPopup();
      data.listUpdaterTask.init(popup, new JBListUpdater(list), new Ref<>());

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

  public static @NotNull <In, Out> List<Annotation> runExternalAnnotator(@NotNull ExternalAnnotator<In, Out> annotator,
                                                                         @NotNull PsiFile psiFile,
                                                                         In in,
                                                                         @NotNull Consumer<? super Out> resultChecker) {
    Out result = annotator.doAnnotate(in);
    resultChecker.accept(result);
    return AnnotationSessionImpl.computeWithSession(psiFile, false, annotator, annotationHolder -> {
      ApplicationManager.getApplication().runReadAction(() -> ((AnnotationHolderImpl)annotationHolder).applyExternalAnnotatorWithContext(psiFile, result));
      ((AnnotationHolderImpl)annotationHolder).assertAllAnnotationsCreated();
      return List.copyOf(((AnnotationHolderImpl)annotationHolder));
    });
  }

  /**
   * Create AnnotationHolder, run {@code annotator} in it on passed {@code elements} and return created Annotations
   */
  public static @NotNull List<Annotation> testAnnotator(@NotNull Annotator annotator, @NotNull PsiElement @NotNull... elements) {
    PsiFile psiFile = elements[0].getContainingFile();
    return AnnotationSessionImpl.computeWithSession(psiFile, false, annotator, annotationHolder -> {
      for (PsiElement element : elements) {
        ((AnnotationHolderImpl)annotationHolder).runAnnotatorWithContext(element);
      }
      ((AnnotationHolderImpl)annotationHolder).assertAllAnnotationsCreated();
      return List.copyOf(((AnnotationHolderImpl)annotationHolder));
    });
  }

  public static void runIdentifierHighlighterPass(@NotNull PsiFile psiFile, @NotNull Editor editor) {
    IdentifierHighlighterUpdater pass = new IdentifierHighlighterUpdater(psiFile, editor, EditorContextManager.getEditorContext(editor, psiFile.getProject()),
                                                                         InjectedLanguageManager.getInstance(psiFile.getProject()).getTopLevelFile(psiFile));
    assert pass != null;
    try {
      ReadAction.nonBlocking(() -> {
        DaemonProgressIndicator indicator = new DaemonProgressIndicator();
        ProgressManager.getInstance().runProcess(() -> {
          // todo IJPL-339 figure out what is the correct context here
          HighlightingSessionImpl.runInsideHighlightingSession(psiFile, FileViewProviderUtil.getCodeInsightContext(psiFile), editor.getColorsScheme(), ProperTextRange.create(psiFile.getTextRange()), false, session -> {
            pass.doCollectInformationForTestsSynchronously(session);
          });
        }, indicator);
      }).submit(AppExecutorUtil.getAppExecutorService()).get();
    }
    catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }
}
