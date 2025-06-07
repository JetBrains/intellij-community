// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.propertyBased;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LexerTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jetCheck.Generator;
import org.junit.Assert;

import java.util.function.Function;

/**
 * Checks that incrementally updated editor highlighter produces the same result as it would
 * after full text lexing. This makes sense to test if your language has:
 * <li>
 *   <ul>Complex highlighting lexer, e.g. with some additional non-jflex state inside</ul>
 *   <ul>Complex highlighter. e.g. {@link com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter}</ul>
 *   <ul>Language depending on file content</ul>
 * </li>
 * 
 */
public class CheckHighlighterConsistency extends ActionOnFile {

  public CheckHighlighterConsistency(@NotNull PsiFile file) {
    super(file);
  }

  @Override
  public void performCommand(@NotNull Environment env) {
    env.logMessage(toString());
    Editor editor = FileEditorManager.getInstance(getProject()).getSelectedTextEditor();
    assert editor.getDocument() == getDocument();

    performCheck(editor);
  }

  public static void performCheck(@NotNull Editor editor) {
    LexerEditorHighlighter highlighter = (LexerEditorHighlighter)editor.getHighlighter();
    CharSequence text = editor.getDocument().getImmutableCharSequence();
    String incremental = dumpHighlighterTokens(highlighter, text);

    highlighter.setText("");
    highlighter.setText(text);
    String full = dumpHighlighterTokens(highlighter, text);

    if (!full.equals(incremental)) {
      Assert.assertEquals("Full lexer highlighter:\n" + full, "Incremental lexer highlighter:\n" + incremental);
    }
  }

  private static @NotNull String dumpHighlighterTokens(LexerEditorHighlighter highlighter, CharSequence text) {
    StringBuilder tokens = new StringBuilder();
    HighlighterIterator iterator = highlighter.createIterator(0);
    while (!iterator.atEnd()) {
      tokens.append(iterator.getStart()).append(" ")
        .append(LexerTestCase.printSingleToken(text, iterator.getTokenType(), iterator.getStart(), iterator.getEnd()));
      iterator.advance();
    }
    return tokens.toString();
  }

  public static final @NotNull Function<PsiFile, Generator<? extends MadTestingAction>> randomEditsWithHighlighterChecks = file -> {
    FileEditorManager.getInstance(file.getProject()).openFile(file.getVirtualFile(), true);
    return Generator.sampledFrom(new CheckHighlighterConsistency(file),
                                 new InsertString(file),
                                 new DeleteRange(file));
  };

}
