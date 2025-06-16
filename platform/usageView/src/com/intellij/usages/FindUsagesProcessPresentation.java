// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;

public class FindUsagesProcessPresentation {
  private final UsageViewPresentation myUsageViewPresentation;

  private boolean myShowPanelIfOnlyOneUsage;
  private boolean myShowNotFoundMessage;
  private Collection<VirtualFile> myLargeFiles;
  private boolean myShowFindOptionsPrompt = true;
  private volatile Runnable mySearchWithProjectFiles;
  private volatile boolean myCanceled;

  public FindUsagesProcessPresentation(@NotNull UsageViewPresentation presentation) {
    myUsageViewPresentation = presentation;
  }

  public boolean isShowNotFoundMessage() {
    return myShowNotFoundMessage;
  }

  public void setShowNotFoundMessage(final boolean showNotFoundMessage) {
    myShowNotFoundMessage = showNotFoundMessage;
  }

  public boolean isShowPanelIfOnlyOneUsage() {
    return myShowPanelIfOnlyOneUsage;
  }

  public void setShowPanelIfOnlyOneUsage(final boolean showPanelIfOnlyOneUsage) {
    myShowPanelIfOnlyOneUsage = showPanelIfOnlyOneUsage;
  }

  public @Nullable Runnable searchIncludingProjectFileUsages() {
    return mySearchWithProjectFiles;
  }

  public void projectFileUsagesFound(@NotNull Runnable searchWithProjectFiles) {
    mySearchWithProjectFiles = searchWithProjectFiles;
  }

  public void setLargeFilesWereNotScanned(@NotNull Collection<VirtualFile> largeFiles) {
    myLargeFiles = largeFiles;
  }

  public @NotNull @Unmodifiable Collection<VirtualFile> getLargeFiles() {
    return myLargeFiles == null ? Collections.emptyList() : myLargeFiles;
  }

  public boolean isShowFindOptionsPrompt() {
    return myShowFindOptionsPrompt;
  }

  public @NotNull UsageViewPresentation getUsageViewPresentation() {
    return myUsageViewPresentation;
  }

  public void setShowFindOptionsPrompt(boolean showFindOptionsPrompt) {
    myShowFindOptionsPrompt = showFindOptionsPrompt;
  }


  public void setCanceled(boolean canceled) {
    myCanceled = canceled;
  }

  public boolean isCanceled() {
    return myCanceled;
  }
}


