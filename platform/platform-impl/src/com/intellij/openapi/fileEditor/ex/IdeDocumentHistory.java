// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.ex;

import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class IdeDocumentHistory {
  public static IdeDocumentHistory getInstance(Project project) {
    return project.getService(IdeDocumentHistory.class);
  }

  public abstract void includeCurrentCommandAsNavigation();
  public abstract void setCurrentCommandHasMoves();
  public abstract void includeCurrentPlaceAsChangePlace();
  public abstract void clearHistory();

  public abstract void back();
  public abstract void forward();

  public abstract boolean isBackAvailable();
  public abstract boolean isForwardAvailable();

  public abstract void navigatePreviousChange();
  public abstract void navigateNextChange();
  public abstract boolean isNavigatePreviousChangeAvailable();
  public abstract boolean isNavigateNextChangeAvailable();

  public abstract @NotNull List<VirtualFile> getChangedFiles();

  @ApiStatus.Internal
  public abstract List<IdeDocumentHistoryImpl.PlaceInfo> getChangePlaces();
  @ApiStatus.Internal
  public abstract List<IdeDocumentHistoryImpl.PlaceInfo> getBackPlaces();

  @ApiStatus.Internal
  public abstract void removeChangePlace(@NotNull IdeDocumentHistoryImpl.PlaceInfo placeInfo);
  @ApiStatus.Internal
  public abstract void removeBackPlace(@NotNull IdeDocumentHistoryImpl.PlaceInfo placeInfo);

  @ApiStatus.Internal
  public abstract void gotoPlaceInfo(@NotNull IdeDocumentHistoryImpl.PlaceInfo info);

  @ApiStatus.Internal
  public abstract void gotoPlaceInfo(@NotNull IdeDocumentHistoryImpl.PlaceInfo info, boolean focusEditor);

  @ApiStatus.Internal
  public abstract void onSelectionChanged();

  /**
   * IdeDocumentHistory#onSelectionChanged can add the current command to the navigation history,
   * even if IdeDocumentHistory#includeCurrentCommandAsNavigation was not called.
   * This method ensures that the current command is excluded from the navigation history,
   * even if there were attempts to add it to the navigation history by other methods.
   */
  @ApiStatus.Experimental
  public abstract void reallyExcludeCurrentCommandAsNavigation();

  @ApiStatus.Internal
  public abstract boolean isSame(@NotNull IdeDocumentHistoryImpl.PlaceInfo first, @NotNull IdeDocumentHistoryImpl.PlaceInfo second);
}
