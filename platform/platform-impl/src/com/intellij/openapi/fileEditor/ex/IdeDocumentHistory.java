// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.ex;

import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
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

  public abstract List<IdeDocumentHistoryImpl.PlaceInfo> getChangePlaces();
  public abstract List<IdeDocumentHistoryImpl.PlaceInfo> getBackPlaces();

  public abstract void removeChangePlace(@NotNull IdeDocumentHistoryImpl.PlaceInfo placeInfo);
  public abstract void removeBackPlace(@NotNull IdeDocumentHistoryImpl.PlaceInfo placeInfo);

  public abstract void gotoPlaceInfo(@NotNull IdeDocumentHistoryImpl.PlaceInfo info);

  public abstract void gotoPlaceInfo(@NotNull IdeDocumentHistoryImpl.PlaceInfo info, boolean focusEditor);
}
