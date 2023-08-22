// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.util.textCompletion.TextCompletionCache;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Bas Leijdekkers
 */
public class TextFieldWithAutoCompletionWithBrowseButton
  extends ComponentWithBrowseButton<TextFieldWithAutoCompletion<String>> implements TextAccessor {

  public TextFieldWithAutoCompletionWithBrowseButton(Project project) {
    super(TextFieldWithAutoCompletion.create(project, Collections.emptyList(), false, null), null);
  }

  public TextFieldWithAutoCompletionWithBrowseButton(Project project, TextCompletionCache<String> cache,
                                                     boolean prefixMatchesOnly) {
    super(TextFieldWithAutoCompletionWithCache.create(cache, prefixMatchesOnly, project, null, false, null), null);
  }

  public TextFieldWithAutoCompletionWithBrowseButton(Project project, TextCompletionCache<String> cache,
                                                     boolean prefixMatchesOnly, boolean showBottomPanel) {
    super(TextFieldWithAutoCompletionWithCache
            .create(cache, prefixMatchesOnly, project, null, false, null, showBottomPanel), null);
  }

  public TextFieldWithAutoCompletionWithBrowseButton(Project project, TextCompletionCache<String> cache,
                                                     boolean prefixMatchesOnly, boolean showBottomPanel, boolean allowAutoPopupWithSlash) {
    super(TextFieldWithAutoCompletionWithCache
            .create(cache, prefixMatchesOnly, project, null, false, null, showBottomPanel, allowAutoPopupWithSlash, false), null);
  }

  public TextFieldWithAutoCompletionWithBrowseButton(Project project, TextCompletionCache<String> cache,
                                                     boolean prefixMatchesOnly, boolean showBottomPanel, boolean allowAutoPopupWithSlash, boolean forbidWordCompletion) {
    super(TextFieldWithAutoCompletionWithCache
            .create(cache, prefixMatchesOnly, project, null, false, null, showBottomPanel, allowAutoPopupWithSlash, forbidWordCompletion), null);
  }

  @Override
  public String getText() {
    return getChildComponent().getText();
  }

  public void setAutoCompletionItems(Collection<String> items) {
    getChildComponent().setVariants(items);
  }

  @Override
  public void setText(String text) {
    getChildComponent().setText(text);
  }
}

