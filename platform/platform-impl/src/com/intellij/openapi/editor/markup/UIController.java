// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * {@code UIController} contains methods for filling inspection widget popup and
 * reacting to changes in the popup.
 * Created lazily only when needed and once for every {@code AnalyzerStatus} instance.
 */
@ApiStatus.Internal
public interface UIController {
  /**
   * Returns {@code true} if the inspection widget can be visible as a toolbar or
   * {@code false} if it can be visible as an icon above the scrollbar only.
   */
  default boolean isToolbarEnabled() {
    return false;
  }

  /**
   * Contains all possible actions in the settings menu. The {@code List} is wrapped
   * in ActionGroup at the UI creation level in {@code EditorMarkupModelImpl}
   */
  @NotNull
  default List<AnAction> getActions() {
    return Collections.emptyList();
  }

  /**
   * Lists possible {@code InspectionLevel}s for the particular file.
   */
  @NotNull
  List<InspectionsLevel> getAvailableLevels();

  /**
   * Lists highlight levels for the particular file per language if the file
   * contains several languages.
   */
  @NotNull
  List<LanguageHighlightLevel> getHighlightLevels();

  /**
   * Saves the {@code LanguageHighlightLevel} for the file.
   */
  void setHighLightLevel(@NotNull LanguageHighlightLevel newLevel);

  /**
   * Adds panels coming from {@code com.intellij.hectorComponentProvider} EP providers to
   * the inspection widget popup.
   */
  void fillHectorPanels(@NotNull Container container, @NotNull GridBag gc);

  /**
   * Can the inspection widget popup be closed. Might be necessary to complete some
   * settings in hector panels before closing the popup.
   * If a panel can be closed and is modified then the settings are applied for the panel.
   */
  boolean canClosePopup();

  /**
   * Called after the popup has been closed. Usually used to dispose resources held by
   * hector panels.
   */
  void onClosePopup();

  void toggleProblemsView();

  @NotNull
  UIController EMPTY = new UIController() {
    @Override
    public @NotNull List<InspectionsLevel> getAvailableLevels() {
      return Collections.emptyList();
    }

    @Override
    public @NotNull List<LanguageHighlightLevel> getHighlightLevels() {
      return Collections.emptyList();
    }

    @Override
    public void setHighLightLevel(@NotNull LanguageHighlightLevel newLevel) {

    }

    @Override
    public void fillHectorPanels(@NotNull Container container, @NotNull GridBag gc) {

    }

    @Override
    public boolean canClosePopup() {
      return false;
    }

    @Override
    public void onClosePopup() {

    }

    @Override
    public void toggleProblemsView() {

    }
  };
}