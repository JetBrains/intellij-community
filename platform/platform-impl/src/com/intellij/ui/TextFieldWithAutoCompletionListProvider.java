// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.textCompletion.DefaultTextCompletionValueDescriptor;
import com.intellij.util.textCompletion.TextCompletionProvider;
import com.intellij.util.textCompletion.TextCompletionValueDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;

/**
 * Extend this provider for:
 * <ol><li> caching (implement {@link #getItems(String, boolean, CompletionParameters)}, see {@link #fillCompletionVariants(CompletionParameters, String, CompletionResultSet)});
 * <li> changing completion variants (see {@link #setItems(Collection)}).</ol>
 * <p>
 * Otherwise, use {@link com.intellij.util.textCompletion.ValuesCompletionProvider} for completion from a fixed set of elements
 * or {@link com.intellij.util.TextFieldCompletionProvider} in other cases.
 *
 * @author Roman.Chernyatchik
 */
public abstract class TextFieldWithAutoCompletionListProvider<T> extends DefaultTextCompletionValueDescriptor<T>
  implements TextCompletionProvider, PossiblyDumbAware {

  private static final Logger LOG = Logger.getInstance(TextFieldWithAutoCompletionListProvider.class);
  protected @NotNull Collection<T> myVariants;

  private @Nullable @NlsContexts.PopupAdvertisement String myCompletionAdvertisement;

  protected TextFieldWithAutoCompletionListProvider(final @Nullable Collection<T> variants) {
    setItems(variants);
    myCompletionAdvertisement = null;
  }

  @Override
  public @Nullable String getPrefix(@NotNull String text, int offset) {
    return getCompletionPrefix(text, offset);
  }

  @Override
  public @NotNull CompletionResultSet applyPrefixMatcher(@NotNull CompletionResultSet result, @NotNull String prefix) {
    PrefixMatcher prefixMatcher = createPrefixMatcher(prefix);
    if (prefixMatcher != null) {
      return result.withPrefixMatcher(prefixMatcher);
    }
    return result;
  }

  @Override
  public @Nullable CharFilter.Result acceptChar(char c) {
    return null;
  }

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters,
                                     @NotNull String prefix,
                                     @NotNull CompletionResultSet result) {
    var cachedItems = addCachedItems(parameters, prefix, result);
    addNonCachedItems(parameters, prefix, result, cachedItems);
  }

  private Collection<T> addCachedItems(@NotNull CompletionParameters parameters, @NotNull String prefix, @NotNull CompletionResultSet result) {
    Collection<T> items = getItems(prefix, true, parameters);
    addCompletionElements(result, this, items, -10000);
    return items;
  }

  private void addNonCachedItems(@NotNull CompletionParameters parameters, @NotNull String prefix, @NotNull CompletionResultSet result,
                                 @NotNull Collection<T> cachedItems) {
    final ProgressManager progressManager = ProgressManager.getInstance();
    ProgressIndicator mainIndicator = progressManager.getProgressIndicator();
    final ProgressIndicator indicator = mainIndicator != null ? new SensitiveProgressWrapper(mainIndicator) : new EmptyProgressIndicator();
    Future<Collection<T>> future = ApplicationManager
      .getApplication()
      .executeOnPooledThread(() -> progressManager.runProcess(() -> getItems(prefix, false, parameters), indicator));

    try {
      Collection<T> items = ProgressIndicatorUtils.awaitWithCheckCanceled(future, indicator);
      if (items != null) {
        var toRemove = new HashSet<>(cachedItems);
        addCompletionElements(result, this, ContainerUtil.filter(items, task -> !toRemove.contains(task)), 0);
      }
    }
    catch (CancellationException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  protected static <T> void addCompletionElements(final CompletionResultSet result,
                                                  final TextCompletionValueDescriptor<T> descriptor,
                                                  final Collection<? extends T> items,
                                                  final int index) {
    final AutoCompletionPolicy completionPolicy = ApplicationManager.getApplication().isUnitTestMode()
                                                  ? AutoCompletionPolicy.ALWAYS_AUTOCOMPLETE
                                                  : AutoCompletionPolicy.NEVER_AUTOCOMPLETE;
    int grouping = index;
    for (final T item : items) {
      if (item == null) {
        LOG.error("Null item from " + descriptor);
        continue;
      }

      LookupElementBuilder builder = descriptor.createLookupBuilder(item);
      result.addElement(PrioritizedLookupElement.withGrouping(builder.withAutoCompletionPolicy(completionPolicy), grouping--));
    }
  }

  public void setItems(final @Nullable Collection<T> variants) {
    myVariants = (variants != null) ? variants : Collections.emptyList();
  }

  public @NotNull Collection<T> getItems(String prefix, boolean cached, CompletionParameters parameters) {
    if (prefix == null) {
      return Collections.emptyList();
    }

    final List<T> items = new ArrayList<>(myVariants);

    items.sort(this);
    return items;
  }

  /**
   * Completion list advertisement text, if null advertisement for documentation
   * popup will be shown
   *
   * @return text
   */
  @Override
  public @Nullable @NlsContexts.PopupAdvertisement String getAdvertisement() {
    if (myCompletionAdvertisement != null) return myCompletionAdvertisement;
    String shortcut = KeymapUtil.getFirstKeyboardShortcutText((IdeActions.ACTION_QUICK_JAVADOC));
    String advertisementTail = getQuickDocHotKeyAdvertisementTail(shortcut);
    if (advertisementTail == null) {
      return null;
    }
    return LangBundle.message("textfield.autocompletion.advertisement", shortcut, advertisementTail);
  }

  protected @Nullable String getQuickDocHotKeyAdvertisementTail(final @NotNull String shortcut) {
    return null;
  }

  public void setAdvertisement(@Nullable @NlsContexts.PopupAdvertisement String completionAdvertisement) {
    myCompletionAdvertisement = completionAdvertisement;
  }

  public @Nullable PrefixMatcher createPrefixMatcher(final @NotNull String prefix) {
    return new PlainPrefixMatcher(prefix);
  }

  public static @NotNull String getCompletionPrefix(CompletionParameters parameters) {
    String text = parameters.getOriginalFile().getText();
    int offset = parameters.getOffset();
    return getCompletionPrefix(text, offset);
  }

  private static @NotNull String getCompletionPrefix(String text, int offset) {
    int i = text.lastIndexOf(' ', offset - 1) + 1;
    int j = text.lastIndexOf('\n', offset - 1) + 1;
    return text.substring(Math.max(i, j), offset);
  }
}
