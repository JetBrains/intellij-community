package com.intellij.formatting;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.formatter.CompositeWhiteSpaceFormattingStrategy;
import com.intellij.psi.formatter.StaticSymbolWhiteSpaceDefinitionStrategy;
import com.intellij.psi.formatter.WhiteSpaceFormattingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class TestFormattingModel implements FormattingModel, FormattingDocumentModel{

  private final CompositeWhiteSpaceFormattingStrategy myWhiteSpaceStrategy = new CompositeWhiteSpaceFormattingStrategy(
    Arrays.<WhiteSpaceFormattingStrategy>asList(
      new StaticSymbolWhiteSpaceDefinitionStrategy(' ', '\t', '\n')
    )
  );
  private final Document myDocument;
  private Block myRootBlock;

  public TestFormattingModel(String text) {
    myDocument = new DocumentImpl(text);
  }

  public TestFormattingModel(final Document document) {
    myDocument = document;
  }

  public void setRootBlock(final Block rootBlock) {
    myRootBlock = rootBlock;
  }

  @Override
  public int getLineNumber(int offset) {
    return myDocument.getLineNumber(offset);
  }

  @Override
  public int getLineStartOffset(int line) {
    return myDocument.getLineStartOffset(line);
  }

  @Override
  public TextRange replaceWhiteSpace(final TextRange textRange,
                                final String whiteSpace
  ) {
    if (ApplicationManager.getApplication() != null) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          myDocument.replaceString(textRange.getStartOffset(), textRange.getEndOffset(), whiteSpace);
        }
      });
    } else {
      myDocument.replaceString(textRange.getStartOffset(), textRange.getEndOffset(), whiteSpace);
    }

    return new TextRange(textRange.getStartOffset(), textRange.getStartOffset() + whiteSpace.length());
  }

  @Override
  public CharSequence getText(final TextRange textRange) {
    return myDocument.getCharsSequence().subSequence(textRange.getStartOffset(), textRange.getEndOffset());
  }

  @Override
  @NotNull
  public FormattingDocumentModel getDocumentModel() {
    return this;
  }

  @Override
  @NotNull
  public Block getRootBlock() {
    return myRootBlock;
  }

  @Override
  public void commitChanges() {
  }

  @Override
  public int getTextLength() {
    return myDocument.getTextLength();
  }

  @NotNull
  @Override
  public Document getDocument() {
    return myDocument;
  }

  @Override
  public TextRange shiftIndentInsideRange(TextRange range, int indent) {
    return range;
  }

  @Override
  public boolean containsWhiteSpaceSymbolsOnly(int startOffset, int endOffset) {
    return myWhiteSpaceStrategy.check(myDocument.getCharsSequence(), startOffset, endOffset) >= endOffset;
  }

  @NotNull
  @Override
  public CharSequence adjustWhiteSpaceIfNecessary(@NotNull CharSequence whiteSpaceText, int startOffset, int endOffset,
                                                  ASTNode nodeAfter, boolean changedViaPsi) {
    return whiteSpaceText;
  }

  //@Override
  //public boolean isWhiteSpaceSymbol(char symbol) {
  //  return containsWhiteSpaceSymbolsOnly(CharBuffer.wrap(new char[] {symbol}), 0, 1);
  //}

  //private boolean containsWhiteSpaceSymbolsOnly(CharSequence text, int startOffset, int endOffset) {
  //  return myWhiteSpaceStrategy.check(myDocument.getCharsSequence(), startOffset, endOffset) >= endOffset;
  //}
}
