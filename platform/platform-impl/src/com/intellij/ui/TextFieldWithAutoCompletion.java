// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.textCompletion.TextCompletionUtil;
import com.intellij.util.textCompletion.TextFieldWithCompletion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * Text field with completion from a list of values.
 *
 * Differs from {@link TextFieldWithCompletion} in 2 aspects:
 * 1. only accepts instances of {@link TextFieldWithAutoCompletionListProvider} (and not other implementations of {@link com.intellij.util.textCompletion.TextCompletionProvider});
 * 2. allows to change completion variants {@link #setVariants(Collection)}.
 *
 * Completion is implemented via {@link com.intellij.util.textCompletion.TextCompletionContributor}.
 *
 * @author Roman Chernyatchik
 */
public class TextFieldWithAutoCompletion<T> extends TextFieldWithCompletion {
  public static final TextFieldWithAutoCompletionListProvider EMPTY_COMPLETION = new StringsCompletionProvider(null, null);
  private final @NotNull TextFieldWithAutoCompletionListProvider<T> myProvider;

  public TextFieldWithAutoCompletion(@Nullable Project project,
                                     @NotNull TextFieldWithAutoCompletionListProvider<T> provider,
                                     boolean showCompletionHint,
                                     @Nullable String text) {
    this(project, provider, showCompletionHint, false, text);
  }

  public TextFieldWithAutoCompletion(@Nullable Project project,
                                     @NotNull TextFieldWithAutoCompletionListProvider<T> provider,
                                     boolean showCompletionHint,
                                     boolean forbidWordCompletion,
                                     @Nullable String text) {
    super(project, provider, text == null ? "" : text, true, true, false, showCompletionHint, forbidWordCompletion);
    myProvider = provider;
  }

  public static @NotNull TextFieldWithAutoCompletion<String> create(@Nullable Project project,
                                                                    @NotNull Collection<String> items,
                                                                    boolean showCompletionHint,
                                                                    @Nullable String text) {
    return create(project, items, null, showCompletionHint, text);
  }

  public static @NotNull TextFieldWithAutoCompletion<String> create(@Nullable Project project,
                                                                    @NotNull Collection<String> items,
                                                                    @Nullable Icon icon,
                                                                    boolean showCompletionHint,
                                                                    @Nullable String text) {
    return new TextFieldWithAutoCompletion<>(project, new StringsCompletionProvider(items, icon), showCompletionHint, text);
  }

  public void setVariants(@NotNull Collection<T> variants) {
    myProvider.setItems(variants);
  }

  public <T> void installProvider(@NotNull TextFieldWithAutoCompletionListProvider<T> provider) {
    installCompletion(getDocument(), getProject(), provider, true);
  }

  public static void installCompletion(@NotNull Document document,
                                       @NotNull Project project,
                                       @NotNull TextFieldWithAutoCompletionListProvider provider,
                                       boolean autoPopup) {
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (psiFile != null) {
      TextCompletionUtil.installProvider(psiFile, provider, autoPopup);
    }
  }

  public static class StringsCompletionProvider extends TextFieldWithAutoCompletionListProvider<String> implements DumbAware {
    private final @Nullable Icon myIcon;

    public StringsCompletionProvider(@Nullable Collection<String> variants, @Nullable Icon icon) {
      super(variants);
      myIcon = icon;
    }

    @Override
    public int compare(String item1, String item2) {
      return StringUtil.compare(item1, item2, false);
    }

    @Override
    protected Icon getIcon(@NotNull String item) {
      return myIcon;
    }

    @Override
    protected @NotNull String getLookupString(@NotNull String item) {
      return item;
    }
  }
}
