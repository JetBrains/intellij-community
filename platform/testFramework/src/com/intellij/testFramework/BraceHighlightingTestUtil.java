// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Helps a brace matching test that keeps the expected pair position in the test data.
 * The test data marks the matched brace with {@code <pair>} or {@code &lt;pair>} directly before it.
 */
public final class BraceHighlightingTestUtil {
  private static final String PAIR_MARKER = "<pair>";
  private static final String PAIR_MARKER2 = "&lt;pair>";

  private BraceHighlightingTestUtil() { }

  /**
   * Finds the one test data file whose name without extension is {@code testName}.
   */
  public static @NotNull VirtualFile getTestFile(@NotNull String testName, @NotNull String basePath, @NotNull String testDataPath) {
    Path directory = Path.of(testDataPath + basePath + testName).getParent();
    VirtualFile vDir = StandardFileSystems.local().findFileByPath(directory.toAbsolutePath().toString());
    assertNotNull(vDir);
    List<VirtualFile> vFiles = Stream.of(vDir.getChildren())
      .filter(file -> file.getNameWithoutExtension().equals(testName))
      .toList();
    assertEquals(1, vFiles.size());
    return vFiles.getFirst();
  }

  /**
   * Removes the pair marker from the document and checks that the brace at the caret matches the brace at the marker.
   *
   * @return the offset of the matched brace
   */
  public static int assertBraceMatched(@NotNull PsiFile file, @NotNull Editor editor) {
    final String pairMarker;
    final int pairOffset;
    if (!file.getText().contains(PAIR_MARKER)) {
      pairMarker = PAIR_MARKER2;
      pairOffset = file.getText().indexOf(pairMarker);
    }
    else {
      pairOffset = file.getText().indexOf(PAIR_MARKER);
      pairMarker = PAIR_MARKER;
    }
    final Document document = editor.getDocument();
    WriteCommandAction.runWriteCommandAction(null, () -> document.replaceString(pairOffset, pairOffset + pairMarker.length(), ""));
    PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
    assertEquals(pairOffset, BraceMatchingUtil.getMatchedBraceOffset(editor, editor.getCaretModel().getOffset() < pairOffset, file));
    return pairOffset;
  }
}
