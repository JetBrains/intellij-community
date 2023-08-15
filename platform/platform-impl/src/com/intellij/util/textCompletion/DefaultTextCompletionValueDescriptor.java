// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.textCompletion;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class DefaultTextCompletionValueDescriptor<T> implements TextCompletionValueDescriptor<T> {
  protected abstract @NotNull String getLookupString(@NotNull T item);

  protected @Nullable Icon getIcon(@NotNull T item) {
    return null;
  }

  protected @Nullable String getTailText(@NotNull T item) {
    return null;
  }

  protected @Nullable String getTypeText(@NotNull T item) {
    return null;
  }

  protected @Nullable InsertHandler<LookupElement> createInsertHandler(final @NotNull T item) {
    return null;
  }

  @Override
  public int compare(T item1, T item2) {
    return StringUtil.compare(getLookupString(item1), getLookupString(item2), false);
  }

  @Override
  public @NotNull LookupElementBuilder createLookupBuilder(@NotNull T item) {
    LookupElementBuilder builder = LookupElementBuilder.create(item, getLookupString(item))
      .withIcon(getIcon(item));

    InsertHandler<LookupElement> handler = createInsertHandler(item);
    if (handler != null) {
      builder = builder.withInsertHandler(handler);
    }

    String tailText = getTailText(item);
    if (tailText != null) {
      builder = builder.withTailText(tailText, true);
    }

    String typeText = getTypeText(item);
    if (typeText != null) {
      builder = builder.withTypeText(typeText);
    }
    return builder;
  }

  public static class StringValueDescriptor extends DefaultTextCompletionValueDescriptor<String> {
    @Override
    public @NotNull String getLookupString(@NotNull String item) {
      return item;
    }
  }
}
