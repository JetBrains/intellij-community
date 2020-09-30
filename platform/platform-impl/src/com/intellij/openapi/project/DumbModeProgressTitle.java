// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.ide.nls.NlsMessages;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts.ProgressTitle;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.IndexingBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

/**
 * We need the way to alter the 'Updating indices' message
 * to allow shared indexes to include it's information into
 * the progress. e.g. IDEA-250576
 */
@Service //project
@ApiStatus.Experimental
public final class DumbModeProgressTitle {
  private final Set<ProgressWindow> myWindowSet = ContainerUtil.newConcurrentSet();
  private final Collection<@ProgressTitle @NotNull String> myTitleAddons = Collections.synchronizedSet(new LinkedHashSet<>());

  @NotNull
  public static DumbModeProgressTitle getInstance(@NotNull Project project) {
    return project.getService(DumbModeProgressTitle.class);
  }

  @SuppressWarnings("unused")
  public DumbModeProgressTitle(@NotNull Project project) {
  }

  @NotNull
  @ProgressTitle
  public String getDumbModeProgressTitle() {
    var mainMessage = IndexingBundle.message("progress.indexing");
    if (myTitleAddons.isEmpty()) return mainMessage;

    return Stream.concat(Stream.of(mainMessage), myTitleAddons.stream()).collect(NlsMessages.joiningNarrowAnd());
  }

  @Nullable
  private static ProgressWindow castProgress(@NotNull ProgressIndicator indicator) {
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

  public void removeDumpModeProgress(@NotNull ProgressIndicator indicator) {
    ProgressWindow wnd = castProgress(indicator);
    if (wnd != null) {
      myWindowSet.remove(wnd);
    }
  }

  public void attachProgressTitleText(@NotNull @Nls String textAddon, @NotNull Disposable lifetime) {
    myTitleAddons.add(textAddon);
    updateAllRegisteredProgressWindows();
    Disposer.register(lifetime, () -> {
      if (myTitleAddons.remove(textAddon)) {
        updateAllRegisteredProgressWindows();
      }
    });
  }

  private void updateAllRegisteredProgressWindows() {
    var newTitle = getDumbModeProgressTitle();
    for (var wnd : myWindowSet) {
      wnd.setTitle(newTitle);
    }
  }
}
