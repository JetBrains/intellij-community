// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.function.Function;

import static com.intellij.openapi.actionSystem.ex.ActionUtil.getMnemonicAsShortcut;
import static com.intellij.openapi.util.NlsActions.ActionText;

@ApiStatus.Internal
public class TreeSpeedSearchInsideCollapsedNodes extends TreeSpeedSearch {

  private @Nullable SearchInsideCollapsedNodesOptionButton searchOptionButton;
  private final @Nullable @ActionText String searchOptionButtonText;
  private @Nullable ShortcutSet searchOptionShortcutSet;

  protected TreeSpeedSearchInsideCollapsedNodes(@NotNull JTree tree,
                                                boolean canExpand,
                                                @Nullable @ActionText String searchOptionButtonText,
                                                @NotNull Function<? super TreePath, String> presentableStringFunction) {
    super(tree, canExpand, null, presentableStringFunction);
    this.searchOptionButtonText = searchOptionButtonText;
    setSearchOptionAction(new SearchInsideCollapsedNodesAction());
  }

  protected @Nullable ActionButton getSearchOptionButton() {
    return searchOptionButton;
  }

  protected void setSearchOptionAction(@NotNull AnAction searchAction) {
    searchOptionButton = new SearchInsideCollapsedNodesOptionButton(searchAction);
    searchOptionShortcutSet = getMnemonicAsShortcut(searchAction);
  }

  @Override
  protected void processKeyEvent(@NotNull KeyEvent e) {
    if (searchOptionButton != null && searchOptionButton.isShowing() && searchOptionShortcutSet != null) {
      KeyStroke eventKeyStroke = KeyStroke.getKeyStrokeForEvent(e);
      boolean match = Arrays.stream(searchOptionShortcutSet.getShortcuts())
        .filter(s -> s.isKeyboard())
        .map(s -> ((KeyboardShortcut)s))
        .anyMatch(s -> eventKeyStroke.equals(s.getFirstKeyStroke()) || eventKeyStroke.equals(s.getSecondKeyStroke()));
      if (match) {
        searchOptionButton.click();
        e.consume();
        return;
      }
    }
    super.processKeyEvent(e);
  }

  @Override
  protected @NotNull SearchPopup createPopup(@Nullable String s) {
    return new SearchInsideCollapsedNodesPopup(s);
  }

  protected boolean canExpand() {
    return myCanExpand;
  }

  protected void showHint() {}

  private class SearchInsideCollapsedNodesPopup extends SearchPopup {
    protected final JPanel myIconsPanel = new NonOpaquePanel();

    protected SearchInsideCollapsedNodesPopup(String initialString) {
      super(initialString);
      add(myIconsPanel, BorderLayout.EAST);
      myIconsPanel.setBorder(JBUI.Borders.emptyRight(5));
      if (searchOptionButton != null) {
        myIconsPanel.add(searchOptionButton);
      }
    }

    @Override
    protected void handleInsert(String newText) {
      if (findElement(newText) == null) {
        mySearchField.setForeground(ERROR_FOREGROUND_COLOR);
        if(!myCanExpand) {
          showHint();
        }
      }
      else {
        mySearchField.setForeground(FOREGROUND_COLOR);
      }
    }
  }

  private class SearchInsideCollapsedNodesAction extends ToggleAction implements TooltipDescriptionProvider {
    SearchInsideCollapsedNodesAction() {
      super(searchOptionButtonText);
      getTemplatePresentation().setIcon(AllIcons.General.Tree);
      getTemplatePresentation().setHoveredIcon(AllIcons.General.TreeHovered);
      getTemplatePresentation().setSelectedIcon(AllIcons.General.TreeSelected);
      ShortcutSet shortcut = getMnemonicAsShortcut(this);
      if (shortcut != null) {
        setShortcutSet(shortcut);
      }
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return canExpand();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      setCanExpand(state);
    }
  }

  private static final class SearchInsideCollapsedNodesOptionButton extends ActionButton {

    private SearchInsideCollapsedNodesOptionButton(@NotNull AnAction action) {
      super(action, action.getTemplatePresentation().clone(), ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
      setLook(ActionButtonLook.INPLACE_LOOK);
      setFocusable(true);
      updateIcon();
    }

    @Override
    protected DataContext getDataContext() {
      return DataManager.getInstance().getDataContext(this);
    }

    @Override
    public int getPopState() {
      return isSelected() ? SELECTED : super.getPopState();
    }

    @Override
    public Icon getIcon() {
      if (isEnabled() && isSelected()) {
        Icon selectedIcon = myPresentation.getSelectedIcon();
        if (selectedIcon != null) return selectedIcon;
      }
      return super.getIcon();
    }
  }
}
