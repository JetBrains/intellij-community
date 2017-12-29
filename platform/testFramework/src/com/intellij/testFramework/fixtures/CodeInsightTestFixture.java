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
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.*;
import com.intellij.ui.components.breadcrumbs.Crumb;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Consumer;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Avdeev
 * @link http://www.jetbrains.org/intellij/sdk/docs/basics/testing_plugins.html
 * @see IdeaTestFixtureFactory#createCodeInsightFixture(IdeaProjectTestFixture)
 */
public interface CodeInsightTestFixture extends IdeaProjectTestFixture {
  String CARET_MARKER = EditorTestUtil.CARET_TAG;
  String ERROR_MARKER = "error";
  String WARNING_MARKER = "warning";
  String WEAK_WARNING_MARKER = "weak_warning";
  String INFO_MARKER = "info";
  String END_LINE_HIGHLIGHT_MARKER = "EOLError";
  String END_LINE_WARNING_MARKER = "EOLWarning";

  /**
   * Returns the in-memory editor instance.
   *
   * @return the in-memory editor instance.
   */
  Editor getEditor();

  /**
   * Returns the offset of the caret in the in-memory editor instance.
   *
   * @return the offset of the caret in the in-memory editor instance.
   */
  int getCaretOffset();

  /**
   * Returns the file currently loaded into the in-memory editor.
   *
   * @return the file currently loaded into the in-memory editor.
   */
  PsiFile getFile();

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
   * @return the VirtualFile for the copied file in the test project directory.
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
   */
  PsiFile configureByFile(@TestDataFile @NotNull String filePath);

  /**
   * Copies multiple files from the testdata directory to the same relative paths in the test project directory
   * and opens the first of them in the in-memory editor.
   *
   * @param filePaths path to the files, relative to the testdata path.
   * @return the PSI files for the copied files.
   */
  @NotNull
  PsiFile[] configureByFiles(@TestDataFile @NotNull String... filePaths);

  /**
   * Loads the specified text, treated as the contents of a file with the specified file type, into the in-memory
   * editor.
   *
   * @param fileType the file type according to which which the text is interpreted.
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
   * Compares the contents of the in-memory editor with the specified file. The trailing whitespaces are not ignored
   * by the comparison.
   *
   * @param expectedFile path to file to check against, relative to the testdata path.
   */
  void checkResultByFile(@TestDataFile @NotNull String expectedFile);

  /**
   * Compares the contents of the in-memory editor with the specified file, optionally ignoring trailing whitespaces.
   *
   * @param expectedFile              path to file to check against, relative to the testdata path.
   * @param ignoreTrailingWhitespaces whether trailing whitespaces should be ignored by the comparison.
   */
  void checkResultByFile(@TestDataFile @NotNull String expectedFile, boolean ignoreTrailingWhitespaces);

  /**
   * Compares a file in the test project with a file in the testdata directory.
   *
   * @param filePath                  path to file to be checked, relative to the source root of the test project.
   * @param expectedFile              path to file to check against, relative to the testdata path.
   * @param ignoreTrailingWhitespaces whether trailing whitespaces should be ignored by the comparison.
   */
  void checkResultByFile(@NotNull String filePath,
                         @TestDataFile @NotNull String expectedFile,
                         boolean ignoreTrailingWhitespaces);

  /**
   * Enables inspections for highlighting tests.
   * Should be called BEFORE {@link #setUp()}. And do not forget to call {@link #tearDown()}
   *
   * @param inspections inspections to be enabled in highlighting tests.
   * @see #enableInspections(InspectionToolProvider...)
   */
  void enableInspections(@NotNull InspectionProfileEntry... inspections);

  void enableInspections(@NotNull Class<? extends LocalInspectionTool>... inspections);

  void enableInspections(@NotNull Collection<Class<? extends LocalInspectionTool>> inspections);

  void disableInspections(@NotNull InspectionProfileEntry... inspections);

  /**
   * Enable all inspections provided by given providers.
   *
   * @param providers providers to be enabled.
   * @see #enableInspections(Class[])
   */
  void enableInspections(@NotNull InspectionToolProvider... providers);

  /**
   * Runs highlighting test for the given files.
   * Checks for {@link #ERROR_MARKER} markers by default.
   * <p/>
   * Double quotes in "descr" attribute of markers must be escaped by either one or two backslashes
   * (see {@link ExpectedHighlightingData#extractExpectedHighlightsSet(Document)}).
   *
   * @param checkWarnings     enables {@link #WARNING_MARKER} support.
   * @param checkInfos        enables {@link #INFO_MARKER} support.
   * @param checkWeakWarnings enables {@link #WEAK_WARNING_MARKER} support.
   * @param filePaths         the first file is tested only; the others are just copied along the first.
   * @return highlighting duration in milliseconds.
   */
  long testHighlighting(boolean checkWarnings,
                        boolean checkInfos,
                        boolean checkWeakWarnings,
                        @TestDataFile @NotNull String... filePaths);

  long testHighlightingAllFiles(boolean checkWarnings,
                                boolean checkInfos,
                                boolean checkWeakWarnings,
                                @TestDataFile @NotNull String... filePaths);

  long testHighlightingAllFiles(boolean checkWarnings,
                                boolean checkInfos,
                                boolean checkWeakWarnings,
                                @TestDataFile @NotNull VirtualFile... files);

  /**
   * Check highlighting of file already loaded by configure* methods
   *
   * @return duration
   */
  long checkHighlighting(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings);

  long checkHighlighting(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings, boolean ignoreExtraHighlighting);

  long checkHighlighting();

  /**
   * Runs highlighting test for the given files.
   * The same as {@link #testHighlighting(boolean, boolean, boolean, String...)} with {@code checkInfos=false}.
   *
   * @param filePaths the first file is tested only; the others are just copied along with the first.
   * @return highlighting duration in milliseconds
   */
  long testHighlighting(@TestDataFile @NotNull String... filePaths);

  long testHighlighting(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings, @NotNull VirtualFile file);

  @NotNull
  HighlightTestInfo testFile(@NotNull String... filePath);

  void openFileInEditor(@NotNull VirtualFile file);

  void testInspection(@NotNull String testDir, @NotNull InspectionToolWrapper toolWrapper);

  /**
   * @return all highlight infos for current file
   */
  @NotNull
  List<HighlightInfo> doHighlighting();

  @NotNull
  List<HighlightInfo> doHighlighting(@NotNull HighlightSeverity minimalSeverity);

  /**
   * Finds the reference in position marked by {@link #CARET_MARKER}.
   *
   * @return null if no reference found.
   * @see #getReferenceAtCaretPositionWithAssertion(String...)
   */
  @Nullable
  PsiReference getReferenceAtCaretPosition(@TestDataFile @NotNull String... filePaths);

  /**
   * Finds the reference in position marked by {@link #CARET_MARKER}.
   * Asserts that the reference exists.
   *
   * @return founded reference
   * @see #getReferenceAtCaretPosition(String...)
   */
  @NotNull
  PsiReference getReferenceAtCaretPositionWithAssertion(@TestDataFile @NotNull String... filePaths);

  /**
   * Collects available intentions at caret position.
   *
   * @param filePaths the first file is tested only; the others are just copied along with the first.
   * @return available intentions.
   * @see #CARET_MARKER
   */
  @NotNull
  List<IntentionAction> getAvailableIntentions(@TestDataFile @NotNull String... filePaths);

  @NotNull
  List<IntentionAction> getAllQuickFixes(@TestDataFile @NotNull String... filePaths);

  @NotNull
  List<IntentionAction> getAvailableIntentions();

  /**
   * Returns all intentions or quick fixes which are available at the current caret position and whose text starts with the specified hint text.
   *
   * @param hint the text that the intention text should begin with.
   * @return the list of matching intentions
   */
  @NotNull
  List<IntentionAction> filterAvailableIntentions(@NotNull String hint);

  /**
   * Returns a single intention or quickfix which is available at the current caret position and whose text starts with the specified
   * hint text. Throws an assertion if no such intentions are found or if multiple intentions match the hint text.
   *
   * @param hint the text that the intention text should begin with.
   * @return the matching intention
   * @throws AssertionError if no intentions are found or if multiple intentions match the hint text.
   */
  @NotNull
  IntentionAction findSingleIntention(@NotNull String hint);

  /**
   * Copies multiple files from the testdata directory to the same relative paths in the test project directory, opens the first of them
   * in the in-memory editor and returns an intention action or quickfix with the name exactly matching the specified text.
   *
   * @param intentionName the text that the intention text should be equal to.
   * @param filePaths     the list of file path to copy to the test project directory.
   * @return the first found intention or quickfix, or null if no matching intention actions are found.
   */
  @Nullable
  IntentionAction getAvailableIntention(@NotNull String intentionName, @TestDataFile @NotNull String... filePaths);

  /**
   * Launches the given action. Use {@link #checkResultByFile(String)} to check the result.
   *
   * @param action the action to be launched.
   */
  void launchAction(@NotNull IntentionAction action);

  void testCompletion(@NotNull String[] filesBefore, @TestDataFile @NotNull String fileAfter);

  void testCompletionTyping(@NotNull String[] filesBefore, @NotNull String toType, @NotNull @TestDataFile String fileAfter);

  /**
   * Runs basic completion in caret position in fileBefore.
   * Implies that there is only one completion variant and it was inserted automatically, and checks the result file text with fileAfter
   */
  void testCompletion(@TestDataFile @NotNull String fileBefore,
                      @NotNull @TestDataFile String fileAfter,
                      @TestDataFile @NotNull String... additionalFiles);

  void testCompletionTyping(@NotNull @TestDataFile String fileBefore,
                            @NotNull String toType,
                            @NotNull @TestDataFile String fileAfter,
                            @TestDataFile @NotNull String... additionalFiles);

  /**
   * Runs basic completion in caret position in fileBefore.
   * Checks that lookup is shown and it contains items with given lookup strings
   *
   * @param items most probably will contain > 1 items
   */
  void testCompletionVariants(@NotNull @TestDataFile String fileBefore, @NotNull String... items);

  /**
   * Launches renaming refactoring and checks the result.
   *
   * @param fileBefore original file path. Use {@link #CARET_MARKER} to mark the element to rename.
   * @param fileAfter  result file to be checked against.
   * @param newName    new name for the element.
   * @see #testRename(String, String)
   */
  void testRename(@NotNull @TestDataFile String fileBefore,
                  @NotNull @TestDataFile String fileAfter,
                  @NotNull String newName,
                  @TestDataFile @NotNull String... additionalFiles);

  void testRename(@NotNull @TestDataFile String fileAfter, @NotNull String newName);

  @NotNull
  Collection<UsageInfo> testFindUsages(@TestDataFile @NotNull String... fileNames);

  @NotNull
  Collection<UsageInfo> findUsages(@NotNull PsiElement to);

  @NotNull
  RangeHighlighter[] testHighlightUsages(@NotNull @TestDataFile String... files);

  void moveFile(@NotNull @TestDataFile String filePath, @NotNull String to, @TestDataFile @NotNull String... additionalFiles);

  /**
   * Returns gutter renderer at the caret position.
   * Use {@link #CARET_MARKER} to mark the element to check.
   *
   * @param filePath file path
   * @return gutter renderer at the caret position.
   */
  @Nullable
  GutterMark findGutter(@NotNull @TestDataFile String filePath);

  @NotNull
  List<GutterMark> findGuttersAtCaret();

  @NotNull
  PsiManager getPsiManager();

  /**
   * @return null if the only item was auto-completed
   * @see #completeBasicAllCarets(Character)
   */
  LookupElement[] completeBasic();

  /**
   * @return null if the only item was auto-completed
   */
  LookupElement[] complete(@NotNull CompletionType type);

  /**
   * @return null if the only item was auto-completed
   */
  LookupElement[] complete(@NotNull CompletionType type, int invocationCount);

  void checkResult(@NotNull String text);

  void checkResult(@NotNull String text, boolean stripTrailingSpaces);

  void checkResult(@NotNull String filePath, @NotNull String text, boolean stripTrailingSpaces);

  Document getDocument(@NotNull PsiFile file);

  @NotNull
  List<GutterMark> findAllGutters(@NotNull @TestDataFile String filePath);

  List<GutterMark> findAllGutters();

  void type(final char c);

  void type(@NotNull String s);

  void performEditorAction(@NotNull String actionId);

  /**
   * If the action is visible and enabled, perform it
   *
   * @return updated action's presentation
   */
  @NotNull
  Presentation testAction(@NotNull AnAction action);

  @Nullable
  List<String> getCompletionVariants(@NotNull @TestDataFile String... filesBefore);

  /**
   * @return null if the only item was auto-completed
   */
  @Nullable
  LookupElement[] getLookupElements();

  VirtualFile findFileInTempDir(@NotNull String filePath);

  @Nullable
  List<String> getLookupElementStrings();

  void finishLookup(@MagicConstant(valuesFromClass = Lookup.class) char completionChar);

  LookupEx getLookup();

  @NotNull
  PsiElement getElementAtCaret();

  void renameElementAtCaret(@NotNull String newName);

  /**
   * Renames element at caret using injected {@link com.intellij.refactoring.rename.RenameHandler}s.
   * Very close to {@link #renameElementAtCaret(String)} but uses handlers.
   *
   * @param newName new name for the element.
   */
  void renameElementAtCaretUsingHandler(@NotNull String newName);

  void renameElement(@NotNull PsiElement element, @NotNull String newName);

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

  void testInlays();

  void checkResultWithInlays(String text);

  void assertPreferredCompletionItems(int selected, @NotNull String... expected);

  /**
   * Initializes the structure view for the file currently loaded in the editor and passes it to the specified consumer.
   *
   * @param consumer the callback in which the actual testing of the structure view is performed.
   */
  void testStructureView(@NotNull Consumer<StructureViewComponent> consumer);

  /**
   * By default, if the caret in the text passed to {@link #configureByFile(String)} or {@link #configureByText} has an injected fragment
   * at the caret, the test fixture puts the caret into the injected editor. This method allows to turn off this behavior.
   *
   * @param caresAboutInjection true if the fixture should look for an injection at caret, false otherwise.
   */
  void setCaresAboutInjection(boolean caresAboutInjection);

  /**
   * Completes basically (see {@link #completeBasic()}) <strong>all</strong>
   * carets (places marked with {@link #CARET_MARKER} in file. Example:
   * <pre>
   *   PyC&lt;caret&gt; is IDE for Py&lt;caret&gt;
   * </pre>
   * should be completed to
   * <pre>
   *   PyCharm is IDE for Python
   * </pre>
   * Actually, it works just like {@link #completeBasic()} but supports
   * several  {@link #CARET_MARKER}
   *
   * @param charToTypeAfterCompletion after completion this char will be typed if argument is not null.
   *                                  It could be used to complete suggestion with "\t" for example.
   * @return list of all completion elements just like in {@link #completeBasic()}
   * @see #completeBasic()
   */
  @NotNull
  List<LookupElement> completeBasicAllCarets(@Nullable Character charToTypeAfterCompletion);

  /**
   * Get elements found by the Goto Class action called with the given pattern
   * @param pattern a pattern to search for elements
   * @param searchEverywhere indicates whether "include non-project classes" checkbox is selected
   * @param contextForSorting a PsiElement used for "proximity sorting" of the results. The sorting will be disabled if null given.
   * @return a list of the results (likely PsiElements) found for the given pattern
   */
  @NotNull
  List<Object> getGotoClassResults(@NotNull String pattern, boolean searchEverywhere, @Nullable PsiElement contextForSorting);

  /**
   * Get breadcrumbs to be generated for the current cursor position in the loaded file
   * @return a list of the breadcrumbs in the order from the topmost element crumb to the deepest
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
    return getProject();
  }
}