// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jsonpath.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class JsonPathStringManipulator extends AbstractElementManipulator<JsonPathStringLiteral> {
  @Override
  public @Nullable JsonPathStringLiteral handleContentChange(@NotNull JsonPathStringLiteral element,
                                                             @NotNull TextRange range,
                                                             String newContent) throws IncorrectOperationException {
    String originalContent = element.getText();
    TextRange withoutQuotes = getRangeInElement(element);
    JsonPathElementGenerator generator = new JsonPathElementGenerator(element.getProject());
    String replacement =
      StringUtil.unescapeStringCharacters(originalContent.substring(withoutQuotes.getStartOffset(), range.getStartOffset())) +
      newContent +
      StringUtil.unescapeStringCharacters(originalContent.substring(range.getEndOffset(), withoutQuotes.getEndOffset()));
    return (JsonPathStringLiteral)element.replace(generator.createStringLiteral(replacement));
  }

  @Override
  public @NotNull TextRange getRangeInElement(@NotNull JsonPathStringLiteral element) {
    String content = element.getText();
    int startOffset = content.startsWith("'") || content.startsWith("\"") ? 1 : 0;
    int endOffset = content.length() > 1 && (content.endsWith("'") || content.endsWith("\"")) ? -1 : 0;
    return new TextRange(startOffset, content.length() + endOffset);
  }
}
