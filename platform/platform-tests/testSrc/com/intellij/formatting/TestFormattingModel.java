/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.formatting;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
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
      WriteCommandAction.runWriteCommandAction(null, new Runnable() {
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
  public TextRange shiftIndentInsideRange(ASTNode node, TextRange range, int indent) {
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
