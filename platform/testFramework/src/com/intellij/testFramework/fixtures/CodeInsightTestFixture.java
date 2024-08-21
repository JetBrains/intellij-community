// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.find.usages.api.SearchTarget;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.modcommand.ActionContext;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.api.RenameTarget;
import com.intellij.testFramework.*;
import com.intellij.ui.components.breadcrumbs.Crumb;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageTarget;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.intellij.lang.annotations.Language;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/testing-plugins.html">Testing Plugins</a>.
 * @see IdeaTestFixtureFactory#createCodeInsightFixture(IdeaProjectTestFixture)
 */
public interface CodeInsightTestFixture extends IdeaProjectTestFixture {
  String CARET_MARKER = EditorTestUtil.CARET_TAG;
  String ERROR_MARKER = "error";
  String WARNING_MARKER = "warning";
  String WEAK_WARNING_MARKER = "weak_warning";
  String INFO_MARKER = "info";
  String TEXT_ATTRIBUTES_MARKER = "text_attr";
  String END_LINE_HIGHLIGHT_MARKER = "EOLError";
  String END_LINE_WARNING_MARKER = "EOLWarning";

  /**
   * @return the in-memory editor instance.
   */
  Editor getEditor();

  /**
   * @return the offset of the caret in the in-memory editor instance.
   */
  int getCaretOffset();

  /**
   * @return the file currently loaded into the in-memory editor.
   */
  PsiFile getFile();

  /**
   * @return the action context for current in-memory editor
   */
  @RequiresReadLock
  default ActionContext getActionContext() {
    return ActionContext.from(getEditor(), getFile());
  }

  void setTestDataPath(@NotNull String dataPath);

  @NotNull
  String getTestDataPath();

  @NotNull
  String getTempDirPath();

  @NotNull
  TempDirTestFixture getTempDirFixture();

  /**
   * Copies a file from the testdata directory to the same relative path in the test project directory.
   *
   * @param sourceFilePath path to the source file, relative to the testdata path.
   * @return the VirtualFile for the copied file in the test project directory.
   * @see #copyFileToProject(String, String)
   */
  @NotNull
  VirtualFile copyFileToProject(@TestDataFile @NotNull String sourceFilePath);

  /**
   * Copies a file from the testdata directory to the specified path in the test project directory.
   *
   * @param sourceFilePath path to the source file, relative to the testdata path.
   * @param targetPath     path to the destination, relative to the source root of the test project.
   * @return the VirtualFile for the copied file in the test project directory.
   */
  @NotNull
  VirtualFile copyFileToProject(@TestDataFile @NotNull String sourceFilePath, @NotNull String targetPath);

  /**
   * Copies a directory from the testdata directory to the specified path in the test project directory.
   *
   * @param sourceFilePath path to the source directory, relative to the testdata path.
   * @param targetPath     path to the destination, relative to the source root of the test project.
   * @return the VirtualFile for the copied directory in the test project directory.
   */
  @NotNull
  VirtualFile copyDirectoryToProject(@TestDataFile @NotNull String sourceFilePath, @NotNull String targetPath);

  /**
   * Copies a file from the testdata directory to the same relative path in the test project directory
   * and opens it in the in-memory editor.
   *
   * @param filePath path to the file, relative to the testdata path.
   * @return the PSI file for the copied and opened file.
   * @see #configureByFiles(String...)
   */
  PsiFile configureByFile(@TestDataFile @NotNull String filePath);

  /**
   * Copies multiple files from the testdata directory to the same relative paths in the test project directory
   * and opens the first of them in the in-memory editor.
   *
   * @param filePaths path to the files, relative to the testdata path.
   * @return the PSI files for the copied files.
   */
  PsiFile @NotNull [] configureByFiles(@TestDataFile String @NotNull ... filePaths);

  /**
   * Loads the specified text, treated as the contents of a file with the specified file type, into the in-memory
   * editor.
   *
   * @param fileType the file type according to which the text is interpreted.
   * @param text     the text to load into the in-memory editor.
   * @return the PSI file created from the specified text.
   */
  PsiFile configureByText(@NotNull FileType fileType, @NotNull String text);

  /**
   * Loads the specified text, treated as the contents of a file with the specified name, into the in-memory
   * editor.
   *
   * @param fileName the name of the file (which is used to determine the file type based on the registered filename patterns).
   * @param text     the text to load into the in-memory editor.
   * @return the PSI file created from the specified text.
   */
  PsiFile configureByText(@NotNull String fileName, @NotNull String text);

  /**
   * Creates a file with specified name and text content.
   *
   * @param fileName the name of the file (which is used to determine the file type based on the registered filename patterns)
   * @param text     the text to write into the file
   * @return the virtual file created from the specified text
   */
  VirtualFile createFile(@NotNull String fileName, @NotNull String text);

  /**
   * Loads the specified file from the test project directory into the in-memory editor.
   *
   * @param filePath the path of the file to load, relative to the test project source root.
   * @return the PSI file for the loaded file.
   */
  PsiFile configureFromTempProjectFile(@NotNull String filePath);

  /**
   * Loads the specified virtual file from the test project directory into the in-memory editor.
   *
   * @param virtualFile the file to load.
   */
  void configureFromExistingVirtualFile(@NotNull VirtualFile virtualFile);

  /**
   * Creates a file with the specified path and contents in the test project directory.
   *
   * @param relativePath the path for the file to create, relative to the test project source root.
   * @param fileText     the text to put into the created file.
   * @return the PSI file for the created file.
   */
  PsiFile addFileToProject(@NotNull String relativePath, @NotNull String fileText);

  /**
   * Compares the contents of the in-memory editor with the specified file.
   * <p>
   * Trailing whitespaces are not ignored by the comparison, see {@link #checkResult(String, boolean)}.
   *
   * @param expectedFile path to file to check against, relative to the testdata path.
   */
  void checkResultByFile(@TestDataFile @NotNull String expectedFile);

  /**
   * Compares the contents of the in-memory editor with the specified file, optionally ignoring trailing whitespaces.
   *
   * @param expectedFile              path to file to check against, relative to the testdata path.
   * @param ignoreTrailingWhitespaces whether the comparison should ignore trailing whitespaces.
   */
  void checkResultByFile(@TestDataFile @NotNull String expectedFile, boolean ignoreTrailingWhitespaces);

  /**
   * Compares a file in the test project with a file in the testdata directory.
   *
   * @param filePath                  path to file to be checked, relative to the source root of the test project.
   * @param expectedFile              path to file to check against, relative to the testdata path.
   * @param ignoreTrailingWhitespaces whether the comparison should ignore trailing whitespaces.
   */
  void checkResultByFile(@NotNull String filePath,
                         @TestDataFile @NotNull String expectedFile,
                         boolean ignoreTrailingWhitespaces);

  /**
   * Enables inspections for highlighting tests.
   *
   * @param inspections inspections to be enabled in highlighting tests.
   * @see #enableInspections(InspectionToolProvider...)
   */
  void enableInspections(InspectionProfileEntry @NotNull ... inspections);

  @SuppressWarnings("unchecked")
  void enableInspections(Class<? extends LocalInspectionTool> @NotNull ... inspections);

  void enableInspections(@NotNull Collection<Class<? extends LocalInspectionTool>> inspections);

  void disableInspections(InspectionProfileEntry @NotNull ... inspections);

  /**
   * Enable all inspections provided by given providers.
   *
   * @param providers providers to be enabled.
   * @see #enableInspections(Class[])
   */
  void enableInspections(InspectionToolProvider @NotNull ... providers);

  /**
   * Runs highlighting test for the given files.
   * <p>
   * Checks for {@link #ERROR_MARKER} markers by default.
   * <p/>
   * Double quotes in "descr" attribute of markers must be escaped by either one or two backslashes
   * (see {@link ExpectedHighlightingData#extractExpectedHighlightsSet(Document)}).
   *
   * @param checkWarnings     enables {@link #WARNING_MARKER} support.
   * @param checkInfos        enables {@link #INFO_MARKER} support.
   * @param checkWeakWarnings enables {@link #WEAK_WARNING_MARKER} support.
   * @param filePaths         the first file is tested only; the others are just copied along with the first.
   * @return highlighting duration in milliseconds.
   */
  long testHighlighting(boolean checkWarnings,
                        boolean checkInfos,
                        boolean checkWeakWarnings,
                        @TestDataFile String @NotNull ... filePaths);

  long testHighlightingAllFiles(boolean checkWarnings,
                                boolean checkInfos,
                                boolean checkWeakWarnings,
                                @TestDataFile String @NotNull ... filePaths);

  long testHighlightingAllFiles(boolean checkWarnings,
                                boolean checkInfos,
                                boolean checkWeakWarnings,
                                @TestDataFile VirtualFile @NotNull ... files);

  /**
   * Check highlighting of file already loaded by {@code configure*} methods.
   *
   * @return duration
   */
  long checkHighlighting(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings);

  long checkHighlighting(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings, boolean ignoreExtraHighlighting);

  long checkHighlighting();

  /**
   * Runs highlighting test for the given files.
   * <p>
   * The same as {@link #testHighlighting(boolean, boolean, boolean, String...)} with {@code checkInfos=false}.
   *
   * @param filePaths the first file is tested only; the others are just copied along with the first.
   * @return highlighting duration in milliseconds
   */
  long testHighlighting(@TestDataFile String @NotNull ... filePaths);

  long testHighlighting(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings, @NotNull VirtualFile file);

  @NotNull
  HighlightTestInfo testFile(String @NotNull ... filePath);

  void openFileInEditor(@NotNull VirtualFile file);

  void testInspection(@NotNull String testDir, @NotNull InspectionToolWrapper<?, ?> toolWrapper);

  void testInspection(@NotNull String testDir, @NotNull InspectionToolWrapper<?, ?> toolWrapper, @NotNull VirtualFile sourceDir);

  /**
   * @return all highlight infos for current file
   */
  @NotNull
  List<HighlightInfo> doHighlighting();

  @NotNull
  List<HighlightInfo> doHighlighting(@NotNull HighlightSeverity minimalSeverity);

  @NotNull PsiSymbolReference findSingleReferenceAtCaret();

  /**
   * Finds the reference in position marked by {@link #CARET_MARKER}.
   *
   * @return {@code null} if no reference found.
   * @see #getReferenceAtCaretPositionWithAssertion(String...)
   */
  @Nullable
  PsiReference getReferenceAtCaretPosition(@TestDataFile String @NotNull ... filePaths);

  /**
   * Finds the required reference in position marked by {@link #CARET_MARKER}.
   * <p>
   * Asserts that the reference exists.
   *
   * @return found reference
   * @throws AssertionError If no reference exists at the position.
   * @see #getReferenceAtCaretPosition(String...)
   */
  @NotNull
  PsiReference getReferenceAtCaretPositionWithAssertion(@TestDataFile String @NotNull ... filePaths);

  /**
   * Collects available intentions in position marked by {@link #CARET_MARKER}.
   *
   * @param filePaths the first file is tested only; the others are just copied along with the first.
   * @return available intentions.
   */
  @NotNull
  List<IntentionAction> getAvailableIntentions(@TestDataFile String @NotNull ... filePaths);

  @NotNull
  List<IntentionAction> getAllQuickFixes(@TestDataFile String @NotNull ... filePaths);

  @NotNull
  List<IntentionAction> getAvailableIntentions();

  /**
   * Returns all intentions or quick fixes which are available in position marked by {@link #CARET_MARKER},
   * and whose text starts with the specified hint text.
   *
   * @param hint the text that the intention text should begin with.
   * @return the list of matching intentions.
   * @see #findSingleIntention(String)
   */
  @NotNull
  List<IntentionAction> filterAvailableIntentions(@NotNull String hint);

  /**
   * Returns the single intention or quickfix which is available in position marked by {@link #CARET_MARKER}
   * and whose text starts with the specified hint text.
   * <p>
   * Throws an assertion if no such intentions are found or if multiple intentions match the hint text.
   *
   * @param hint the text that the intention text should begin with.
   * @return the single matching intention/quickfix
   * @throws AssertionError if no intentions are found or if multiple intentions match the hint text.
   * @see #launchAction(String)
   */
  @NotNull
  IntentionAction findSingleIntention(@NotNull String hint);

  /**
   * Copies multiple files from the testdata directory to the same relative paths in the test project directory, opens the first of them
   * in the in-memory editor and returns an intention action or quickfix with the name exactly matching the specified text.
   *
   * @param intentionName the text that the intention text should be equal to.
   * @param filePaths     the list of file paths to copy to the test project directory.
   * @return the first found intention or quickfix, or {@code null} if no matching intention actions are found.
   */
  @Nullable
  IntentionAction getAvailableIntention(@NotNull String intentionName, @TestDataFile String @NotNull ... filePaths);

  /**
   * @param action action to get the preview from
   * @return the diff-preview text generated by this action, or null
   * if the action generates non-diff preview
   */
  @Nullable String getIntentionPreviewText(@NotNull IntentionAction action);

  /**
   * Returns diff-preview text generated by action which is available in position marked by {@link #CARET_MARKER}
   * and whose text starts with the specified hint text.
   *
   * @param hint the text that the intention text should begin with.
   * @return the diff-preview text generated by this action, or null
   * if the action generates non-diff preview
   */
  @Nullable String getIntentionPreviewText(@NotNull String hint);

  /**
   * Checks whether intention preview is HTML with expected text.
   *
   * @param action action to get the preview from
   * @param expected expected HTML preview text
   */
  void checkIntentionPreviewHtml(@NotNull IntentionAction action, @NotNull @Language("HTML") String expected);

  /**
   * Launches the given intention action.
   * <p>
   * Use {@link #checkResultByFile(String)} to check the result.
   *
   * @param action the action to be launched.
   */
  void launchAction(@NotNull IntentionAction action);

  /**
   * Launches the single intention or quickfix which is available in position marked by {@link #CARET_MARKER}
   * and whose text starts with the specified hint text.
   * <p>
   * Use {@link #checkResultByFile(String)} to check the result.
   *
   * @param hint the text that the intention text should begin with.
   */
  void launchAction(@NotNull String hint);

  /**
   * Launches the given intention action and checks that the preview it generates is a diff-preview which
   * actually identical to the result of action execution
   * <p>
   * Use {@link #checkResultByFile(String)} to check the result.
   *
   * @param action the action to be launched.
   * @see #launchAction(IntentionAction)
   */
  void checkPreviewAndLaunchAction(@NotNull IntentionAction action);

  void testCompletion(String @NotNull [] filesBefore, @TestDataFile @NotNull String fileAfter);

  void testCompletionTyping(String @NotNull [] filesBefore, @NotNull String toType, @NotNull @TestDataFile String fileAfter);

  /**
   * Runs basic completion in position marked by {@link #CARET_MARKER} in given file {@code fileBefore}.
   * <p>
   * Implies that there is only one completion variant, and it was inserted automatically and checks the result file text {@code fileAfter}.
   */
  void testCompletion(@TestDataFile @NotNull String fileBefore,
                      @NotNull @TestDataFile String fileAfter,
                      @TestDataFile String @NotNull ... additionalFiles);

  void testCompletionTyping(@NotNull @TestDataFile String fileBefore,
                            @NotNull String toType,
                            @NotNull @TestDataFile String fileAfter,
                            @TestDataFile String @NotNull ... additionalFiles);

  /**
   * Runs basic completion in position marked by {@link #CARET_MARKER} in given file {@code fileBefore}.
   * <p>
   * Checks that lookup is shown, and it contains items with given lookup strings
   *
   * @param items most probably will contain > 1 items
   */
  void testCompletionVariants(@NotNull @TestDataFile String fileBefore, String @NotNull ... items);

  /**
   * Opens the specified file in the editor, launches the rename refactoring on the PSI element in position marked by {@link #CARET_MARKER} and checks the result.
   * <p>
   * For new tests, please use {@link #testRenameUsingHandler} instead.
   *
   * @param fileBefore original file path.
   * @param fileAfter  result file to be checked against.
   * @param newName    new name for the element.
   */
  void testRename(@NotNull @TestDataFile String fileBefore,
                  @NotNull @TestDataFile String fileAfter,
                  @NotNull String newName,
                  @TestDataFile String @NotNull ... additionalFiles);

  /**
   * Opens the specified file in the editor, launches the rename refactoring in position marked by {@link #CARET_MARKER} using the rename handler (using the high-level
   * rename API, as opposed to retrieving the PSI element at caret and invoking the PSI rename on it) and checks the result.
   *
   * @param fileBefore original file path.
   * @param fileAfter  result file to be checked against.
   * @param newName    new name for the element.
   * @see #testRename(String, String)
   * @see #renameElementAtCaretUsingHandler(String)
   */
  void testRenameUsingHandler(@NotNull @TestDataFile String fileBefore,
                              @NotNull @TestDataFile String fileAfter,
                              @NotNull String newName,
                              @TestDataFile String @NotNull ... additionalFiles);

  /**
   * Launches the rename refactoring on the PSI element in position marked by {@link #CARET_MARKER} and checks the result.
   * <p>
   * For new tests, please use {@link #testRenameUsingHandler} instead.
   */
  void testRename(@NotNull @TestDataFile String fileAfter, @NotNull String newName);

  /**
   * Launches the rename refactoring in position marked by {@link #CARET_MARKER} using the rename handler (using the high-level rename API, as opposed to
   * retrieving the PSI element at caret and invoking the PSI rename on it) and checks the result.
   *
   * @see #renameElementAtCaretUsingHandler(String)
   */
  void testRenameUsingHandler(@NotNull @TestDataFile String fileAfter, @NotNull String newName);

  /**
   * Invokes the Find Usages handler for the PSI element in position marked by {@link #CARET_MARKER} and returns the usages returned by it.
   * <p>
   * For new tests, please use {@link #testFindUsagesUsingAction} instead.
   */
  @NotNull
  Collection<UsageInfo> testFindUsages(@TestDataFile String @NotNull ... fileNames);

  /**
   * Opens the specified file in the editor, places the caret and selection according to the markup,
   * launches the Find Usages action and returns the items displayed in the usage view.
   */
  @NotNull
  Collection<Usage> testFindUsagesUsingAction(@TestDataFile String @NotNull ... fileNames);

  @NotNull
  Collection<UsageInfo> findUsages(@NotNull PsiElement to);

  /**
   * @return a text representation of {@link com.intellij.usages.UsageView} created from the usages.
   */
  @NotNull
  String getUsageViewTreeTextRepresentation(@NotNull Collection<? extends UsageInfo> usages);

  @NotNull String getUsageViewTreeTextRepresentation(@NotNull List<UsageTarget> usageTargets, @NotNull Collection<? extends Usage> usages);

  /**
   * @return a text representation of {@link com.intellij.usages.UsageView} created from usages of {@code to}
   * <p>
   * The result of this method could be more verbose than {@code getUsageViewTreeTextRepresentation(findUsages(to))}.
   */
  @NotNull
  String getUsageViewTreeTextRepresentation(@NotNull PsiElement to);

  @NotNull String getUsageViewTreeTextRepresentation(@NotNull SearchTarget target);

  RangeHighlighter @NotNull [] testHighlightUsages(@TestDataFile String @NotNull ... files);

  void moveFile(@NotNull @TestDataFile String filePath, @NotNull String to, @TestDataFile String @NotNull ... additionalFiles);

  /**
   * Returns gutter renderer in position marked by {@link #CARET_MARKER}.
   *
   * @param filePath file path
   * @return gutter renderer at the caret position.
   * @see #findGuttersAtCaret()
   */
  @Nullable
  GutterMark findGutter(@NotNull @TestDataFile String filePath);

  /**
   * @see #findGutter(String)
   */
  @NotNull
  List<GutterMark> findGuttersAtCaret();

  @NotNull
  PsiManager getPsiManager();

  /**
   * @return {@code null} if the only item was auto-completed.
   * @see #completeBasicAllCarets(Character)
   */
  LookupElement[] completeBasic();

  /**
   * @return {@code null} if the only item was auto-completed.
   */
  LookupElement[] complete(@NotNull CompletionType type);

  /**
   * @return {@code null} if the only item was auto-completed.
   */
  LookupElement[] complete(@NotNull CompletionType type, int invocationCount);

  void checkResult(@NotNull String expectedText);

  void checkResult(@NotNull String expectedText, boolean stripTrailingSpaces);

  void checkResult(@NotNull String filePath, @NotNull String expectedText, boolean stripTrailingSpaces);

  Document getDocument(@NotNull PsiFile file);

  @NotNull
  List<GutterMark> findAllGutters(@NotNull @TestDataFile String filePath);

  List<GutterMark> findAllGutters();

  void type(final char c);

  void type(@NotNull String s);

  default void performEditorAction(@NotNull String actionId) {
    performEditorAction(actionId, null);
  }

  void performEditorAction(@NotNull String actionId, @Nullable AnActionEvent actionEvent);

  /**
   * If the action is visible and enabled, perform it.
   *
   * @return updated action's presentation
   */
  @NotNull
  Presentation testAction(@NotNull AnAction action);

  @Nullable
  List<String> getCompletionVariants(@TestDataFile String @NotNull ... filesBefore);

  /**
   * @return {@code null} if the only item was auto-completed.
   */
  LookupElement @Nullable [] getLookupElements();

  VirtualFile findFileInTempDir(@NotNull String filePath);

  /**
   * @return {@code null} if the only item was auto-completed
   */
  @Nullable
  List<String> getLookupElementStrings();

  void finishLookup(@MagicConstant(valuesFromClass = Lookup.class) char completionChar);

  LookupEx getLookup();

  /**
   * Returns element in position marked by {@link #CARET_MARKER} in the current file ({@link #configureByFile(String)}).
   * <p>
   * This element must implement {@link com.intellij.psi.PsiNamedElement} or it has reference to something:
   * it must be a valid target for rename/find usage action.
   * See {@link com.intellij.codeInsight.TargetElementUtil}.
   * <p>
   * For any other type of element use {@link PsiFile#findElementAt(int)} or {@link #findElementByText(String, Class)}
   */
  @NotNull
  PsiElement getElementAtCaret();

  /**
   * Renames element in position marked by {@link #CARET_MARKER} using direct call of {@link RenameProcessor#run()}
   *
   * @param newName new name for the element.
   * @apiNote method {@link #renameElementAtCaretUsingHandler(String)} is more generic
   * because it does some pre-processing work before calling {@link RenameProcessor#run()}
   */
  void renameElementAtCaret(@NotNull String newName);

  /**
   * Renames element in position marked by {@link #CARET_MARKER} using injected {@link RenameHandler}.
   * <p>
   * Very close to {@link #renameElementAtCaret(String)} but uses handlers.
   *
   * @param newName new name for the element.
   * @apiNote if the handler suggests some substitutions for the element with a dialog
   * you can use {@link TestDialogManager#setTestDialog(TestDialog)} to provide YES/NO answer.
   * Also makes sure that your rename handler properly processes name from {@link PsiElementRenameHandler#DEFAULT_NAME}
   * @see CodeInsightTestUtil#doInlineRename for more sophisticated in-place refactorings
   */
  void renameElementAtCaretUsingHandler(@NotNull String newName);

  /**
   * Renames element using direct call of {@link RenameProcessor#run()}.
   *
   * @param element element to rename
   * @param newName new name for the element
   */
  void renameElement(@NotNull PsiElement element, @NotNull String newName);

  @Experimental
  void renameTarget(@NotNull RenameTarget renameTarget, @NotNull String newName);

  void allowTreeAccessForFile(@NotNull VirtualFile file);

  void allowTreeAccessForAllFiles();

  void renameElement(@NotNull PsiElement element,
                     @NotNull String newName,
                     boolean searchInComments,
                     boolean searchTextOccurrences);

  <T extends PsiElement> T findElementByText(@NotNull String text, @NotNull Class<T> elementClass);

  void testFolding(@NotNull String fileName);

  void testFoldingWithCollapseStatus(@NotNull final String verificationFileName, @Nullable String destinationFileName);

  void testFoldingWithCollapseStatus(@NotNull String fileName);

  void testRainbow(@NotNull String fileName, @NotNull String text, boolean isRainbowOn, boolean withColor);

  /**
   * Misnamed, actually it checks only parameter hints.
   * @see #testInlays(Function, Predicate)
   */
  void testInlays();

  /**
   * @param inlayPresenter function to render text of inlay. Inlays come to this function only if {@code inlayFilter} returned {@code true}.
   * @param inlayFilter    filter to check only required inlays
   */
  void testInlays(Function<? super Inlay<?>, String> inlayPresenter, Predicate<? super Inlay<?>> inlayFilter);

  void checkResultWithInlays(String text);

  void assertPreferredCompletionItems(int selected, String @NotNull ... expected);

  /**
   * Initializes the structure view for the file currently loaded in the editor and passes it to the specified consumer.
   *
   * @param consumer the callback in which the actual testing of the structure view is performed.
   */
  void testStructureView(@NotNull Consumer<? super StructureViewComponent> consumer);

  /**
   * By default, if the caret in the text passed to {@link #configureByFile(String)} or {@link #configureByText} has an injected fragment
   * in position marked by {@link #CARET_MARKER}, the test fixture puts the caret into the injected editor.
   * This method allows turning off this behavior.
   *
   * @param caresAboutInjection {@code true} if the fixture should look for an injection at caret, {@code false} otherwise.
   */
  void setCaresAboutInjection(boolean caresAboutInjection);

  /**
   * By default, {@link #doHighlighting} only collects highlight infos from {@link Document} markup model.
   * Setting this flag will make it also return highlight infos from {@link Editor#getMarkupModel}.
   */
  void setReadEditorMarkupModel(boolean readEditorMarkupModel);

  /**
   * Completes basically (see {@link #completeBasic()}) <strong>all</strong>
   * carets (places marked with {@link #CARET_MARKER} in file).
   * Example:
   * <pre>
   *   PyC&lt;caret&gt; is IDE for Py&lt;caret&gt;
   * </pre>
   * should be completed to
   * <pre>
   *   PyCharm is IDE for Python
   * </pre>
   * Actually, it works just like {@link #completeBasic()} but supports
   * several {@link #CARET_MARKER}.
   *
   * @param charToTypeAfterCompletion after completion, this char will be typed if not {@code null}.
   *                                  It could be used to complete the suggestion with {@code '\t'} for example.
   * @param typeCharIfOnlyOneCompletion if charToTypeAfterCompletion should be placed if code is completed by {@link #completeBasic()}
   * @return list of all completion elements just like in {@link #completeBasic()}
   * @see #completeBasic()
   */
  @NotNull
  List<LookupElement> completeBasicAllCarets(@Nullable Character charToTypeAfterCompletion, boolean typeCharIfOnlyOneCompletion);

  @NotNull
  List<LookupElement> completeBasicAllCarets(@Nullable Character charToTypeAfterCompletion);

  /**
   * Get elements found by the Goto Class action called with the given pattern
   *
   * @param pattern           pattern to search for elements.
   * @param searchEverywhere  indicates whether "include non-project classes" checkbox is selected.
   * @param contextForSorting PsiElement used for "proximity sorting" of the results. Sorting will be disabled if set to {@code null}.
   * @return a list of the results (likely PsiElements) found for the given pattern.
   */
  @NotNull
  List<Object> getGotoClassResults(@NotNull String pattern, boolean searchEverywhere, @Nullable PsiElement contextForSorting);

  /**
   * Get elements found by the Goto Symbol action called with the given pattern.
   *
   * @param pattern           pattern to search for elements.
   * @param searchEverywhere  indicates whether "include non-project classes" checkbox is selected.
   * @param contextForSorting PsiElement used for "proximity sorting" of the results. Sorting will be disabled if set to {@code null}.
   * @return a list of the results (likely PsiElements) found for the given pattern.
   */
  @NotNull
  List<Object> getGotoSymbolResults(@NotNull String pattern, boolean searchEverywhere, @Nullable PsiElement contextForSorting);

  /**
   * Get breadcrumbs to be generated for position marked by {@link #CARET_MARKER} in the loaded file.
   *
   * @return a list of the breadcrumbs in the order from the topmost element crumb to the deepest.
   */
  @NotNull
  List<Crumb> getBreadcrumbsAtCaret();

  void saveText(@NotNull VirtualFile file, @NotNull String text);

  /**
   * @return Disposable for the corresponding project fixture.
   * It's disposed earlier than {@link UsefulTestCase#getTestRootDisposable()} and can be useful
   * e.g. for avoiding library virtual pointers leaks: {@code PsiTestUtil.addLibrary(myFixture.getProjectDisposable(), ...)}
   */
  @NotNull
  default Disposable getProjectDisposable() {
    return ((ProjectEx)getProject()).getEarlyDisposable();
  }
}
