/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.HighlightTestInfo;
import com.intellij.testFramework.TestDataFile;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Consumer;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 *
 * @see IdeaTestFixtureFactory#createCodeInsightFixture(IdeaProjectTestFixture)
 * @link http://confluence.jetbrains.net/display/IDEADEV/Testing+IntelliJ+IDEA+Plugins
 *
 * @author Dmitry Avdeev
 */
public interface CodeInsightTestFixture extends IdeaProjectTestFixture {

  @NonNls String CARET_MARKER = "<caret>";
  @NonNls String SELECTION_START_MARKER = "<selection>";
  @NonNls String SELECTION_END_MARKER = "</selection>";
  @NonNls String BLOCK_START_MARKER = "<block>";
  @NonNls String BLOCK_END_MARKER = "</block>";

  @NonNls String ERROR_MARKER = "error";
  @NonNls String WARNING_MARKER = "warning";
  @NonNls String INFORMATION_MARKER = "weak_warning";
  @NonNls String SERVER_PROBLEM_MARKER = "server_problem";
  @NonNls String INFO_MARKER = "info";
  @NonNls String END_LINE_HIGHLIGHT_MARKER = "EOLError";
  @NonNls String END_LINE_WARNING_MARKER = "EOLWarning";

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

  void setTestDataPath(@NonNls String dataPath);

  String getTestDataPath();

  String getTempDirPath();

  TempDirTestFixture getTempDirFixture();

  /**
   * Copies a file from the testdata directory to the specified path in the test project directory.
   *
   * @param sourceFilePath path to the source file, relative to the testdata path.
   * @param targetPath path to the destination, relative to the source root of the test project.
   * @return the VirtualFile for the copied file in the test project directory.
   */
  VirtualFile copyFileToProject(@TestDataFile @NonNls String sourceFilePath, @NonNls String targetPath);

  /**
   * Copies a directory from the testdata directory to the specified path in the test project directory.
   *
   * @param sourceFilePath path to the source directory, relative to the testdata path.
   * @param targetPath path to the destination, relative to the source root of the test project.
   * @return the VirtualFile for the copied directory in the test project directory.
   */
  VirtualFile copyDirectoryToProject(@NonNls String sourceFilePath, @NonNls String targetPath);

  /**
   * Copies a file from the testdata directory to the same relative path in the test project directory.
   *
   * @return the VirtualFile for the copied file in the test project directory.
   */
  VirtualFile copyFileToProject(@TestDataFile @NonNls String sourceFilePath);

  /**
   * Copies a file from the testdata directory to the same relative path in the test project directory
   * and opens it in the in-memory editor.
   *
   * @param filePath path to the file, relative to the testdata path.
   * @return the PSI file for the copied and opened file.
   */
  PsiFile configureByFile(@TestDataFile @NonNls String filePath);

  /**
   * Copies multiple files from the testdata directory to the same relative paths in the test project directory
   * and opens the first of them in the in-memory editor.
   *
   * @param filePaths path to the files, relative to the testdata path.
   * @return the PSI files for the copied files.
   */
  PsiFile[] configureByFiles(@TestDataFile @NonNls String... filePaths);

  /**
   * Loads the specified text, treated as the contents of a file with the specified file type, into the in-memory
   * editor.
   *
   * @param fileType the file type according to which which the text is interpreted.
   * @param text the text to load into the in-memory editor.
   * @return the PSI file created from the specified text.
   */
  PsiFile configureByText(FileType fileType, @NonNls String text);

  /**
   * Loads the specified text, treated as the contents of a file with the specified name, into the in-memory
   * editor.
   *
   * @param fileName the name of the file (which is used to determine the file type based on the registered filename patterns).
   * @param text the text to load into the in-memory editor.
   * @return the PSI file created from the specified text.
   */
  PsiFile configureByText(String fileName, @NonNls String text);

  /**
   * Loads the specified file from the test project directory into the in-memory editor.
   *
   * @param filePath the path of the file to load, relative to the test project source root.
   * @return the PSI file for the loaded file.
   */
  PsiFile configureFromTempProjectFile(String filePath);

  /**
   * Loads the specified virtual file from the test project directory into the in-memory editor.
   *
   * @param f the file to load.
   */
  void configureFromExistingVirtualFile(VirtualFile f);

  /**
   * Creates a file with the specified path and contents in the test project directory.
   *
   * @param relativePath the path for the file to create, relative to the test project source root.
   * @param fileText the text to put into the created file.
   *
   * @return the PSI file for the created file.
   */
  PsiFile addFileToProject(@NonNls String relativePath, @NonNls String fileText);

  /**
   * Compares the contents of the in-memory editor with the specified file. The trailing whitespaces are not ignored
   * by the comparison.
   *
   * @param expectedFile path to file to check against, relative to the testdata path.
   */
  void checkResultByFile(@TestDataFile @NonNls String expectedFile);

  /**
   * Compares the contents of the in-memory editor with the specified file, optionally ignoring trailing whitespaces.
   *
   * @param expectedFile path to file to check against, relative to the testdata path.
   * @param ignoreTrailingWhitespaces whether trailing whitespaces should be ignored by the comparison.
   */
  void checkResultByFile(@TestDataFile @NonNls String expectedFile, boolean ignoreTrailingWhitespaces);

  /**
   * Compares a file in the test project with a file in the testdata directory.
   *
   * @param filePath path to file to be checked, relative to the source root of the test project.
   * @param expectedFile path to file to check against, relative to the testdata path.
   * @param ignoreTrailingWhitespaces whether trailing whitespaces should be ignored by the comparison.
   */
  void checkResultByFile(@NonNls String filePath, @TestDataFile @NonNls String expectedFile, boolean ignoreTrailingWhitespaces);

  /**
   * Enables inspections for highlighting tests.
   * Should be called BEFORE {@link #setUp()}. And do not forget to call {@link #tearDown()}
   *
   * @param inspections inspections to be enabled in highlighting tests.
   * @see #enableInspections(com.intellij.codeInspection.InspectionToolProvider...)
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
  void enableInspections(InspectionToolProvider... providers);

  /**
   * Runs highlighting test for the given files.
   * Checks for {@link #ERROR_MARKER} markers by default.
   *
   * @param checkWarnings enables {@link #WARNING_MARKER} support.
   * @param checkInfos enables {@link #INFO_MARKER} support.
   * @param checkWeakWarnings enables {@link #INFORMATION_MARKER} support.
   * @param filePaths the first file is tested only; the others are just copied along the first.
   *
   * @return highlighting duration in milliseconds.
   */
  long testHighlighting(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings, @TestDataFile @NonNls String... filePaths);

  long testHighlightingAllFiles(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings, @TestDataFile @NonNls String... filePaths);

  long testHighlightingAllFiles(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings, @TestDataFile @NonNls VirtualFile... files);

  /**
   * Check highlighting of file already loaded by configure* methods
   * @return duration
   */
  long checkHighlighting(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings);

  long checkHighlighting();

  /**
   * Runs highlighting test for the given files.
   * The same as {@link #testHighlighting(boolean, boolean, boolean, String...)} with all options set.
   *
   * @param filePaths the first file is tested only; the others are just copied along with the first.
   *
   * @return highlighting duration in milliseconds
   */
  long testHighlighting(@TestDataFile @NonNls String... filePaths);

  long testHighlighting(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings, VirtualFile file);
  HighlightTestInfo testFile(@NonNls @NotNull String... filePath);

  void testInspection(@NotNull String testDir, @NotNull InspectionToolWrapper toolWrapper);

  /**
   * @return all highlight infos for current file
   */
  @NotNull
  List<HighlightInfo> doHighlighting();

  @NotNull
  List<HighlightInfo> doHighlighting(HighlightSeverity minimalSeverity);

  /**
   * Finds the reference in position marked by {@link #CARET_MARKER}.
   *
   * @return null if no reference found.
   *
   * @see #getReferenceAtCaretPositionWithAssertion(String...)
   */
  @Nullable
  PsiReference getReferenceAtCaretPosition(@TestDataFile @NonNls String... filePaths);

  /**
   * Finds the reference in position marked by {@link #CARET_MARKER}.
   * Asserts that the reference exists.
   *
   * @return founded reference
   *
   * @see #getReferenceAtCaretPosition(String...)
   */
  @NotNull
  PsiReference getReferenceAtCaretPositionWithAssertion(@NonNls @TestDataFile String... filePaths);

  /**
   * Collects available intentions at caret position.
   *
   * @param filePaths the first file is tested only; the others are just copied along with the first.
   * @return available intentions.
   * @see #CARET_MARKER
   */
  @NotNull
  List<IntentionAction> getAvailableIntentions(@NonNls String... filePaths);

  @NotNull
  List<IntentionAction> getAllQuickFixes(@NonNls String... filePaths);

  @NotNull
  List<IntentionAction> getAvailableIntentions();

  /**
   * Returns all intentions or quickfixes which are available at the current caret position and whose text starts with the specified hint text.
   *
   * @param hint the text that the intention text should begin with.
   * @return the list of matching intentions
   */
  List<IntentionAction> filterAvailableIntentions(@NotNull String hint);

  /**
   * Returns a single intention or quickfix which is available at the current caret position and whose text starts with the specified
   * hint text. Throws an assertion if no such intentions are found or if multiple intentions match the hint text.
   *
   * @param hint the text that the intention text should begin with.
   * @return the list of matching intentions
   */
  IntentionAction findSingleIntention(@NotNull String hint);

  /**
   * Copies multiple files from the testdata directory to the same relative paths in the test project directory, opens the first of them
   * in the in-memory editor and returns an intention action or quickfix with the name exactly matching the specified text.
   *
   * @param intentionName the text that the intention text should be equal to.
   * @param filePaths the list of file path to copy to the test project directory.
   * @return the first found intention or quickfix, or null if no matching intention actions are found.
   */
  @Nullable
  IntentionAction getAvailableIntention(final String intentionName, final String... filePaths);

  /**
   * Launches the given action. Use {@link #checkResultByFile(String)} to check the result.
   *
   * @param action the action to be launched.
   */
  void launchAction(@NotNull IntentionAction action);

  void testCompletion(@NonNls String[] filesBefore, @TestDataFile @NonNls String fileAfter);

  void testCompletionTyping(@NonNls String[] filesBefore, String toType, @TestDataFile @NonNls String fileAfter);

  /**
   * Runs basic completion in caret position in fileBefore.
   * Implies that there is only one completion variant and it was inserted automatically, and checks the result file text with fileAfter
   */
  void testCompletion(@TestDataFile @NonNls String fileBefore, @TestDataFile @NonNls String fileAfter, final String... additionalFiles);

  void testCompletionTyping(@TestDataFile @NonNls String fileBefore, String toType, @TestDataFile @NonNls String fileAfter, final String... additionalFiles);

  /**
   * Runs basic completion in caret position in fileBefore.
   * Checks that lookup is shown and it contains items with given lookup strings
   * @param items most probably will contain > 1 items
   */
  void testCompletionVariants(@TestDataFile @NonNls String fileBefore, @NonNls String... items);

  /**
   * Launches renaming refactoring and checks the result.
   *
   * @param fileBefore original file path. Use {@link #CARET_MARKER} to mark the element to rename.
   * @param fileAfter result file to be checked against.
   * @param newName new name for the element.
   * @see #testRename(String, String)
   */
  void testRename(@TestDataFile @NonNls String fileBefore,
                  @TestDataFile @NonNls String fileAfter, @NonNls String newName, final String... additionalFiles);

  void testRename(String fileAfter, String newName);

  Collection<UsageInfo> testFindUsages(@TestDataFile @NonNls String... fileNames);

  Collection<UsageInfo> findUsages(final PsiElement to);

  RangeHighlighter[] testHighlightUsages(String... files);

  void moveFile(@NonNls String filePath, @NonNls String to, final String... additionalFiles);

  /**
   * Returns gutter renderer at the caret position.
   * Use {@link #CARET_MARKER} to mark the element to check.
   *
   * @param filePath file path
   * @return gutter renderer at the caret position.
   */
  @Nullable
  GutterMark findGutter(@TestDataFile @NonNls String filePath);

  PsiManager getPsiManager();

  /**
   * @return null if the only item was auto-completed
   */
  LookupElement[] completeBasic();

  /**
   * @return null if the only item was auto-completed
   */
  LookupElement[] complete(CompletionType type);

  /**
   * @return null if the only item was auto-completed
   */
  LookupElement[] complete(CompletionType type, int invocationCount);

  void checkResult(final String text);

  void checkResult(final String text, boolean stripTrailingSpaces);

  Document getDocument(PsiFile file);

  @NotNull
  Collection<GutterMark> findAllGutters(String filePath);

  void type(final char c);

  void type(final String s);

  void performEditorAction(String actionId);

  /**
   * If the action is visible and enabled, perform it
   * @param action
   * @return updated action's presentation
   */
  Presentation testAction(AnAction action);

  @Nullable
  List<String> getCompletionVariants(String... filesBefore);

  /**
   * @return null if the only item was auto-completed
   */
  @Nullable
  LookupElement[] getLookupElements();

  VirtualFile findFileInTempDir(String filePath);

  @Nullable
  List<String> getLookupElementStrings();

  void finishLookup(@MagicConstant(valuesFromClass = Lookup.class) char completionChar);

  LookupEx getLookup();

  @NotNull
  PsiElement getElementAtCaret();

  void renameElementAtCaret(String newName);

  void renameElement(PsiElement element, String newName);

  void allowTreeAccessForFile(VirtualFile file);

  void allowTreeAccessForAllFiles();

  void renameElement(PsiElement element,
                             String newName,
                             boolean searchInComments,
                             boolean searchTextOccurrences);

  <T extends PsiElement> T findElementByText(String text, Class<T> elementClass);

  void testFolding(String fileName);
  void testFoldingWithCollapseStatus(String fileName);

  void assertPreferredCompletionItems(int selected, @NonNls String... expected);

  /**
   * Initializes the structure view for the file currently loaded in the editor and passes it to the specified consumer.
   *
   * @param consumer the callback in which the actual testing of the structure view is performed.
   */
  void testStructureView(Consumer<StructureViewComponent> consumer);

  /**
   * By default, if the caret in the text passed to {@link #configureByFile(String)} or {@link #configureByText} has an injected fragment
   * at the caret, the test fixture puts the caret into the injected editor. This method allows to turn off this behavior.
   *
   * @param caresAboutInjection true if the fixture should look for an injection at caret, false otherwise.
   */
  void setCaresAboutInjection(boolean caresAboutInjection);
}
