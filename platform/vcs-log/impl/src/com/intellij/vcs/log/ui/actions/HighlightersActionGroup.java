/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.ui.VcsLogHighlighterFactory;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class HighlightersActionGroup extends ActionGroup {
  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    List<AnAction> actions = ContainerUtil.newArrayList();

    if (e != null) {
      VcsLogUi ui = e.getData(VcsLogDataKeys.VCS_LOG_UI);
      if (ui != null) {
        actions.add(new Separator("Highlight"));
        for (VcsLogHighlighterFactory factory : Extensions.getExtensions(VcsLogUiImpl.LOG_HIGHLIGHTER_FACTORY_EP, e.getProject())) {
          if (factory.showMenuItem()) {
            actions.add(new EnableHighlighterAction(ui, factory));
          }
        }
      }
    }

    return actions.toArray(new AnAction[actions.size()]);
  }

  private static class EnableHighlighterAction extends ToggleAction implements DumbAware {
    @NotNull private final VcsLogHighlighterFactory myFactory;
    @NotNull private final VcsLogUi myUi;

    private EnableHighlighterAction(@NotNull VcsLogUi ui, @NotNull VcsLogHighlighterFactory factory) {
      super(factory.getTitle());
      myUi = ui;
      myFactory = factory;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myUi.isHighlighterEnabled(myFactory.getId());
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myUi.setHighlighterEnabled(myFactory.getId(), state);
    }
  }
}
