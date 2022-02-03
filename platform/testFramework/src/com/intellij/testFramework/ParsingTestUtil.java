// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.LineColumn;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.impl.BlockSupportImpl;
import com.intellij.psi.impl.ChangedPsiRangeUtil;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Objects;

public final class ParsingTestUtil {
  private static final String SEPARATOR = "---------------";
  private static final String NL_SEPARATOR_NL = "\n" + SEPARATOR + "\n";

  private ParsingTestUtil() {
  }

  /**
   * Ensures that none of psi subtrees contains {@link PsiErrorElement} or fails
   */
  public static void ensureNoErrorElementsInAllSubTrees(@NotNull PsiFile file) {
    for (PsiFile subTree : file.getViewProvider().getAllFiles()) {
      ensureNoErrorElements(subTree);
    }
  }

  /**
   * Ensures that {@code file} contains no {@link PsiErrorElement} or fails
   *
   * @see #ensureNoErrorElementsInAllSubTrees(PsiFile)
   */
  public static void ensureNoErrorElements(@NotNull PsiFile file) {
    file.accept(new PsiRecursiveElementVisitor() {
      private static final int TAB_WIDTH = 8;

      @Override
      public void visitErrorElement(@NotNull PsiErrorElement element) {
        // Very dump approach since a corresponding Document is not available.
        String text = file.getText();
        String[] lines = StringUtil.splitByLinesKeepSeparators(text);

        int offset = element.getTextOffset();
        LineColumn position = StringUtil.offsetToLineColumn(text, offset);
        int lineNumber = position != null ? position.line : -1;
        int column = position != null ? position.column : 0;

        String line = StringUtil.trimTrailing(lines[lineNumber]);
        // Sanitize: expand indentation tabs, replace the rest with a single space
        int numIndentTabs = StringUtil.countChars(line.subSequence(0, column), '\t', 0, true);
        int indentedColumn = column + numIndentTabs * (TAB_WIDTH - 1);
        String lineWithNoTabs = StringUtil.repeat(" ", numIndentTabs * TAB_WIDTH) + line.substring(numIndentTabs).replace('\t', ' ');
        String errorUnderline = StringUtil.repeat(" ", indentedColumn) + StringUtil.repeat("^", Math.max(1, element.getTextLength()));

        TestCase.fail(String.format("Unexpected error element: %s:%d:%d\n\n%s\n%s\n%s",
                                    file.getName(), lineNumber + 1, column,
                                    lineWithNoTabs, errorUnderline, element.getErrorDescription()));
      }
    });
  }

  /**
   * @return string with all subtrees of the {@code psiFile} serialized with spaces and without ranges
   */
  public static @NotNull String psiFileToString(@NotNull PsiFile psiFile) {
    StringBuilder result = new StringBuilder();
    for (PsiFile subTree : psiFile.getViewProvider().getAllFiles()) {
      result.append("Language: ").append(subTree.getLanguage()).append("\n").append(DebugUtil.psiToString(subTree, true, false));
    }
    return result.toString();
  }

  /**
   * Checks if {@code psiFile} updated with {@code newFileText} can be partially parsed. Stores re-parsing result for all subtrees of the file.
   * Asserts that incremental parsing is consistent with full reparse with the updated text.
   */
  public static void testIncrementalParsing(@NotNull PsiFile psiFile,
                                            @NotNull CharSequence newFileText,
                                            @NotNull String answersFilePath,
                                            boolean checkInitialTreeForErrors,
                                            boolean checkFinalTreeForErrors) {
    if (checkInitialTreeForErrors) {
      ensureNoErrorElements(psiFile);
    }
    var project = psiFile.getProject();
    var psiDocumentManager = PsiDocumentManager.getInstance(project);
    var fileDocument = psiDocumentManager.getDocument(psiFile);
    TestCase.assertNotNull(fileDocument);
    psiDocumentManager.commitDocument(fileDocument);

    var originalText = fileDocument.getCharsSequence();
    StringBuilder result = new StringBuilder("Original text:")
      .append(NL_SEPARATOR_NL)
      .append(originalText)
      .append(NL_SEPARATOR_NL);

    var allFiles = psiFile.getViewProvider().getAllFiles();
    ContainerUtil.sort(allFiles, Comparator.comparing(it -> it.getLanguage().getID()));
    for (PsiFile subTree : allFiles) {
      UsefulTestCase.assertInstanceOf(subTree, PsiFileImpl.class);
      var subTreeFile = (PsiFileImpl)subTree;
      TextRange changedRange =
        ChangedPsiRangeUtil.getChangedPsiRange(subTreeFile, Objects.requireNonNull(subTreeFile.getTreeElement()), newFileText);
      TestCase.assertNotNull("No changes found", changedRange);
      Couple<ASTNode> reparseableRoots = BlockSupportImpl.findReparseableRoots(subTreeFile, subTree.getNode(), changedRange, newFileText);
      result.append("Subtree: ").append(subTree.getLanguage()).append(NL_SEPARATOR_NL);
      serializeReparseableRoots(reparseableRoots, result, newFileText);
      result.append(NL_SEPARATOR_NL);
    }

    WriteAction.run(() -> fileDocument.setText(newFileText));
    psiDocumentManager.commitDocument(fileDocument);
    var psiBeforeCommit = psiFileToString(psiFile);
    WriteCommandAction.runWriteCommandAction(project, () -> {
      fileDocument.setText("");
      psiDocumentManager.commitDocument(fileDocument);
      fileDocument.setText(newFileText);
      psiDocumentManager.commitDocument(fileDocument);
    });

    TestCase.assertEquals("Reparsing error", psiFileToString(psiFile), psiBeforeCommit);
    if (checkFinalTreeForErrors) {
      ensureNoErrorElementsInAllSubTrees(psiFile);
    }
    UsefulTestCase.assertSameLinesWithFile(answersFilePath, result.toString(), false);
  }

  private static void serializeReparseableRoots(@Nullable Couple<ASTNode> reparseableRoots,
                                                @NotNull StringBuilder result,
                                                @NotNull CharSequence newText) {
    TextRange reparsedRange;
    if (reparseableRoots == null) {
      reparsedRange = TextRange.create(0, newText.length());
    }
    else {
      reparsedRange = TextRange.from(reparseableRoots.first.getStartOffset(), reparseableRoots.second.getTextLength());
    }
    result.append(newText, 0, reparsedRange.getStartOffset());
    result.append("<reparse>");
    result.append(newText, reparsedRange.getStartOffset(), reparsedRange.getEndOffset());
    result.append("</reparse>");
    result.append(newText, reparsedRange.getEndOffset(), newText.length());
  }
}
