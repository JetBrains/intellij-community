/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.TestDataFile;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 *
 * @see IdeaTestFixtureFactory#createCodeInsightFixture(IdeaProjectTestFixture)
 *
 * @author Dmitry Avdeev
 */
public interface CodeInsightTestFixture extends IdeaProjectTestFixture {

  @NonNls String CARET_MARKER = "<caret>";
  @NonNls String SELECTION_START_MARKER = "<selection>";
  @NonNls String SELECTION_END_MARKER = "</selection>";

  @NonNls String ERROR_MARKER = "error";
  @NonNls String WARNING_MARKER = "warning";
  @NonNls String INFORMATION_MARKER = "weak_warning";
  @NonNls String SERVER_PROBLEM_MARKER = "server_problem";
  @NonNls String INFO_MARKER = "info";
  @NonNls String END_LINE_HIGHLIGHT_MARKER = "EOLError";
  @NonNls String END_LINE_WARNING_MARKER = "EOLWarning";

  Editor getEditor();
  
  PsiFile getFile();

  void setTestDataPath(@NonNls String dataPath);

  String getTestDataPath();

  String getTempDirPath();

  TempDirTestFixture getTempDirFixture();

  VirtualFile copyFileToProject(@NonNls String sourceFilePath, @NonNls String targetPath) throws IOException;

  VirtualFile copyDirectoryToProject(@NonNls String sourceFilePath, @NonNls String targetPath) throws IOException;

  VirtualFile copyFileToProject(@TestDataFile @NonNls String sourceFilePath) throws IOException;

  /**
   * Enables inspections for highlighting tests.
   * Should be called BEFORE {@link #setUp()}. And do not forget to call {@link #tearDown()}
   *
   * @param inspections inspections to be enabled in highliting tests.
   * @see #enableInspections(com.intellij.codeInspection.InspectionToolProvider[])
   */
  void enableInspections(InspectionProfileEntry... inspections);

  void enableInspections(Class<? extends LocalInspectionTool>... inspections);

  void disableInspections(InspectionProfileEntry... inspections);

  /**
   * Enable all inspections provided by given providers.
   *
   * @param providers providers to be enabled.
   * @see #enableInspections(com.intellij.codeInspection.LocalInspectionTool[])
   */
  void enableInspections(InspectionToolProvider... providers);

  /**
   * Runs highliting test for the given files.
   * Checks for {@link #ERROR_MARKER} markers by default.
   *
   * @param checkWarnings enables {@link #WARNING_MARKER} support.
   * @param checkInfos enables {@link #INFO_MARKER} support.
   * @param checkWeakWarnings enables {@link #INFORMATION_MARKER} support.
   * @param filePaths the first file is tested only; the others are just copied along the first.
   *
   * @return highlighting duration in milliseconds.
   * @throws Exception any exception thrown during highlighting.
   */
  long testHighlighting(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings, @TestDataFile @NonNls String... filePaths) throws Exception;

  long testHighlightingAllFiles(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings, @TestDataFile @NonNls String... filePaths) throws Exception;

  long testHighlightingAllFiles(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings, @NonNls VirtualFile... files) throws Exception;

  /**
   * Check highlighting of file already loaded by configure* methods
   * @param checkWarnings
   * @param checkInfos
   * @param checkWeakWarnings
   * @return
   * @throws Exception
   */
  long checkHighlighting(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings) throws Exception;

  long checkHighlighting() throws Exception;

  /**
   * Runs highlighting test for the given files.
   * The same as {@link #testHighlighting(boolean, boolean, boolean, String...)} with all options set.
   *
   * @param filePaths the first file is tested only; the others are just copied along with the first.
   *
   * @return highlighting duration in milliseconds
   * @throws Exception any exception thrown during highlighting
   */
  long testHighlighting(@NonNls String... filePaths) throws Exception;

  long testHighlighting(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings, VirtualFile file) throws Exception;

  void testInspection(String testDir, InspectionTool tool) throws Exception;

  /**
   * Finds the reference in position marked by {@link #CARET_MARKER}.
   *
   * @param filePaths
   * @return null if no reference found.
   * @throws Exception any exception.
   *
   * @see #getReferenceAtCaretPositionWithAssertion(String...)
   */
  @Nullable
  PsiReference getReferenceAtCaretPosition(@TestDataFile @NonNls String... filePaths) throws Exception;

  /**
   * Finds the reference in position marked by {@link #CARET_MARKER}.
   * Asserts that the reference exists.
   *
   * @param filePaths
   * @return founded reference
   * @throws Exception any exception
   *
   * @see #getReferenceAtCaretPosition(String...)
   */
  @NotNull
  PsiReference getReferenceAtCaretPositionWithAssertion(@NonNls String... filePaths) throws Exception;

  /**
   * Collects available intentions at caret position.
   *
   * @param filePaths the first file is tested only; the others are just copied along with the first.
   * @return available intentions.
   * @throws Exception any exception.
   * @see #CARET_MARKER
   */
  @NotNull
  List<IntentionAction> getAvailableIntentions(@NonNls String... filePaths) throws Exception;

  @NotNull
  List<IntentionAction> getAllQuickFixes(@NonNls String... filePaths) throws Exception;

  @NotNull
  List<IntentionAction> getAvailableIntentions() throws Exception;

  /**
   * Returns all intentions whose text contains hint
   * @param hint
   * @return
   * @throws Exception
   */
  List<IntentionAction> filterAvailableIntentions(@NotNull String hint) throws Exception;

  IntentionAction findSingleIntention(@NotNull String hint) throws Exception;

  IntentionAction getAvailableIntention(final String intentionName, final String... filePaths) throws Exception;

  /**
   * Launches the given action. Use {@link #checkResultByFile(String)} to check the result.
   *
   * @param action the action to be launched.
   * @throws Exception any exception.
   */
  void launchAction(@NotNull IntentionAction action) throws Exception;

  void configureByFile(@TestDataFile @NonNls String file);

  void configureByFiles(@NonNls String... files);

  PsiFile configureByText(FileType fileType, @NonNls String text) throws IOException;

  PsiFile configureByText(String fileName, @NonNls String text) throws IOException;

  /**
   * Compares current file against the given one.
   *
   * @param expectedFile file to check against.
   * @throws Exception any exception.
   */
  void checkResultByFile(@TestDataFile @NonNls String expectedFile) throws Exception;
  
  void checkResultByFile(@TestDataFile @NonNls String expectedFile, boolean ignoreTrailingWhitespaces) throws Exception;

  /**
   * Compares two files.
   *
   * @param filePath file to be checked.
   * @param expectedFile file to check against.
   * @param ignoreTrailingWhitespaces set to true to ignore differences in whitespaces.
   * @throws Exception any exception.
   */
  void checkResultByFile(@NonNls String filePath, @TestDataFile @NonNls String expectedFile, boolean ignoreTrailingWhitespaces) throws Exception;

  void testCompletion(@NonNls String[] filesBefore, @TestDataFile @NonNls String fileAfter) throws Exception;

  /**
   * Runs basic completion in caret position in fileBefore.
   * Implies that there is only one completion variant and it was inserted automatically, and checks the result file text with fileAfter
   * @param fileBefore
   * @param fileAfter
   * @param additionalFiles
   * @throws Exception
   */
  void testCompletion(@TestDataFile @NonNls String fileBefore, @TestDataFile @NonNls String fileAfter, final String... additionalFiles) throws Exception;

  /**
   * Runs basic completion in caret position in fileBefore.
   * Checks that lookup is shown and it contains items with given lookup strings
   * @param fileBefore
   * @param items most probably will contain > 1 items
   * @throws Exception
   */
  void testCompletionVariants(@TestDataFile @NonNls String fileBefore, @NonNls String... items) throws Exception;

  /**
   * Launches renaming refactoring and checks the result.
   *
   * @param fileBefore original file path. Use {@link #CARET_MARKER} to mark the element to rename.
   * @param fileAfter result file to be checked against.
   * @param newName new name for the element.
   * @param additionalFiles
   * @throws Exception any exception.
   * @see #testRename(String, String, String, String[])
   */
  void testRename(@TestDataFile @NonNls String fileBefore,
                  @TestDataFile @NonNls String fileAfter, @NonNls String newName, final String... additionalFiles) throws Exception;

  void testRename(String fileAfter, String newName) throws Exception;

  Collection<UsageInfo> testFindUsages(@NonNls String... fileNames) throws Exception;

  Collection<UsageInfo> findUsages(final PsiElement to) throws Exception;

  RangeHighlighter[] testHighlightUsages(String... files);

  void moveFile(@NonNls String filePath, @NonNls String to, final String... additionalFiles) throws Exception;

  /**
   * Returns gutter renderer at the caret position.
   * Use {@link #CARET_MARKER} to mark the element to check.
   *
   * @param filePath file path
   * @return gutter renderer at the caret position.
   * @throws Exception any exception.
   */
  @Nullable
  GutterIconRenderer findGutter(@NonNls String filePath) throws Exception;

  PsiManager getPsiManager();

  @Nullable LookupElement[] completeBasic();

  @Nullable LookupElement[] complete(CompletionType type);

  void checkResult(final String text);

  void checkResult(final String text, boolean stripTrailingSpaces);

  Document getDocument(PsiFile file);

  void setFileContext(@Nullable PsiElement context);

  @NotNull
  Collection<GutterIconRenderer> findAllGutters(String filePath) throws Exception;

  void type(final char c);

  void performEditorAction(String actionId);

  int configureFromTempProjectFile(String filePath) throws IOException;

  void configureFromExistingVirtualFile(VirtualFile f) throws IOException;

  PsiFile addFileToProject(@NonNls String relativePath, @NonNls String fileText) throws IOException;

  List<String> getCompletionVariants(String... filesBefore) throws Exception;

  @Nullable
  LookupElement[] getLookupElements();

  VirtualFile findFileInTempDir(String filePath);

  @Nullable
  List<String> getLookupElementStrings();

  @NotNull
  PsiElement getElementAtCaret();

  void renameElementAtCaret(String newName) throws Exception;

  void renameElement(PsiElement element, String newName) throws Exception;

  void allowTreeAccessForFile(VirtualFile file);

  void allowTreeAccessForAllFiles();

  void renameElement(PsiElement element,
                             String newName,
                             boolean searchInComments,
                             boolean searchTextOccurrences) throws Exception;
}
