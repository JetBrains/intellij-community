// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.TextRange;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.fail;

public final class ParsingTestUtil {
  private static final String SEPARATOR = "---------------";
  private static final String NL_SEPARATOR_NL = "\n" + SEPARATOR + "\n";

  private ParsingTestUtil() {
  }

  /**
   * Ensures that none of psi subtrees contains {@link PsiErrorElement} or fails
   */
  public static void assertNoPsiErrorElementsInAllSubTrees(@NotNull PsiFile file) {
    for (PsiFile subTree : file.getViewProvider().getAllFiles()) {
      assertNoPsiErrorElements(subTree);
    }
  }

  /**
   * Ensures that {@code file} contains no {@link PsiErrorElement} or fails
   *
   * @see #assertNoPsiErrorElementsInAllSubTrees(PsiFile)
   */
  public static void assertNoPsiErrorElements(@NotNull PsiFile file) {
    List<String> errors = new ArrayList<>();
    file.accept(new PsiRecursiveElementVisitor() {
      @Override
      public void visitErrorElement(@NotNull PsiErrorElement element) {
        errors.add(element.getTextOffset() + ": " + element.getErrorDescription());
        super.visitErrorElement(element);
      }
    });
    if (!errors.isEmpty()) {
      fail("Found PsiElement errors at offsets:\n" + String.join("\n", errors));
    }
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
      assertNoPsiErrorElements(psiFile);
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

    for (PsiFile subTree : ContainerUtil.sorted(allFiles, Comparator.comparing(it -> it.getLanguage().getID()))) {
      UsefulTestCase.assertInstanceOf(subTree, PsiFileImpl.class);
      var subTreeFile = (PsiFileImpl)subTree;
      TextRange changedRange =
        ChangedPsiRangeUtil.getChangedPsiRange(subTreeFile, Objects.requireNonNull(subTreeFile.getTreeElement()), newFileText);
      TestCase.assertNotNull("No changes found", changedRange);
      Couple<ASTNode> reparseableRoots = BlockSupportImpl.findReparseableNodeAndReparseIt(subTreeFile, subTree.getNode(), changedRange, newFileText);
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
      assertNoPsiErrorElementsInAllSubTrees(psiFile);
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
