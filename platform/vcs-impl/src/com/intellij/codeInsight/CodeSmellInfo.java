// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

/**
 * The information about a single problem found during pre-checkin code analysis.
 * @see com.intellij.openapi.vcs.CodeSmellDetector#findCodeSmells(java.util.List)
 * @see com.intellij.openapi.vcs.CodeSmellDetector#showCodeSmellErrors(java.util.List)
 */
public final class CodeSmellInfo {
  private final @NotNull Document myDocument;
  private final String myDescription;
  private final TextRange myTextRange;
  private final HighlightSeverity mySeverity;

  public CodeSmellInfo(final @NotNull Document document, final String description, final TextRange textRange, final HighlightSeverity severity) {
    myDocument = document;
    myDescription = description;
    myTextRange = textRange;
    mySeverity = severity;
  }

  public @NotNull Document getDocument() {
    return myDocument;
  }

  public String getDescription() {
    return myDescription;
  }

  public TextRange getTextRange(){
    return myTextRange;
  }

  public HighlightSeverity getSeverity(){
    return mySeverity;
  }

  public int getStartLine() {
    return getDocument().getLineNumber(getTextRange().getStartOffset());
  }

  public int getStartColumn() {
    return getTextRange().getStartOffset() - getDocument().getLineStartOffset(getStartLine());
  }
}
