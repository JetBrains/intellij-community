// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.AbstractVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.vcs.log.impl.MainVcsLogUiProperties.VcsLogHighlighterProperty;

public class HighlightersActionGroup extends ActionGroup implements DumbAware {
  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    List<AnAction> actions = new ArrayList<>();

    if (e != null) {
      if (e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES) != null) {
        actions.add(new Separator(IdeBundle.messagePointer("action.Anonymous.text.highlight")));
        for (VcsLogHighlighterFactory factory : AbstractVcsLogUi.LOG_HIGHLIGHTER_FACTORY_EP.getExtensionList()) {
          if (factory.showMenuItem()) {
            actions.add(new EnableHighlighterAction(factory));
          }
        }
      }
    }

    return actions.toArray(AnAction.EMPTY_ARRAY);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static final class EnableHighlighterAction extends BooleanPropertyToggleAction {
    private final @NotNull VcsLogHighlighterFactory myFactory;

    private EnableHighlighterAction(@NotNull VcsLogHighlighterFactory factory) {
      super(() -> factory.getTitle());
      myFactory = factory;
    }

    @Override
    protected VcsLogUiProperties.VcsLogUiProperty<Boolean> getProperty() {
      return VcsLogHighlighterProperty.get(myFactory.getId());
    }
  }
}
