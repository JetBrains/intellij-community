// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts.ProgressTitle;
import com.intellij.util.indexing.IndexingBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * We need the way to alter the 'Updating indices' message
 * to allow shared indexes to include it's information into
 * the progress.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
@ApiStatus.Experimental
public final class DumbModeProgressTitle {
  private final Set<ProgressWindow> myWindowSet = ConcurrentCollectionFactory.createConcurrentSet();
  private final Collection<@ProgressTitle @NotNull String> mySubProcessTitles = Collections.synchronizedSet(new LinkedHashSet<>());

  public static @NotNull DumbModeProgressTitle getInstance(@NotNull Project project) {
    return project.getService(DumbModeProgressTitle.class);
  }

  @SuppressWarnings("unused")
  public DumbModeProgressTitle(@NotNull Project project) {
  }

  public @NotNull @ProgressTitle String getDumbModeProgressTitle() {
    var mainMessage = IndexingBundle.message("progress.indexing");
    if (mySubProcessTitles.isEmpty()) return mainMessage;

    return mySubProcessTitles.stream().collect(NlsMessages.joiningNarrowAnd());
  }

  private static @Nullable ProgressWindow castProgress(@NotNull ProgressIndicator indicator) {
    if (indicator instanceof ProgressWindow) {
      return (ProgressWindow)indicator;
    }
    return null;
  }

  public void attachDumbModeProgress(@NotNull ProgressIndicator indicator) {
    ProgressWindow wnd = castProgress(indicator);
    if (wnd != null) {
      myWindowSet.add(wnd);
      wnd.setTitle(getDumbModeProgressTitle());
    }
  }

  public void removeDumbModeProgress(@NotNull ProgressIndicator indicator) {
    ProgressWindow wnd = castProgress(indicator);
    if (wnd != null) {
      myWindowSet.remove(wnd);
    }
  }

  public void attachProgressTitleText(@NotNull @Nls String textAddon, @NotNull Disposable lifetime) {
    mySubProcessTitles.add(textAddon);
    updateAllRegisteredProgressWindows();
    Disposer.register(lifetime, () -> {
      if (mySubProcessTitles.remove(textAddon)) {
        updateAllRegisteredProgressWindows();
      }
    });
  }

  private void updateAllRegisteredProgressWindows() {
    for (var wnd : myWindowSet) {
      wnd.setTitle(getDumbModeProgressTitle());
    }
  }
}
