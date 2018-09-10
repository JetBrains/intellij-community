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

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.AbstractVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.vcs.log.impl.MainVcsLogUiProperties.VcsLogHighlighterProperty;

public class HighlightersActionGroup extends ActionGroup implements DumbAware {
  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    List<AnAction> actions = ContainerUtil.newArrayList();

    if (e != null) {
      if (e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES) != null) {
        actions.add(new Separator("Highlight"));
        for (VcsLogHighlighterFactory factory : Extensions.getExtensions(AbstractVcsLogUi.LOG_HIGHLIGHTER_FACTORY_EP, e.getProject())) {
          if (factory.showMenuItem()) {
            actions.add(new EnableHighlighterAction(factory));
          }
        }
      }
    }

    return actions.toArray(AnAction.EMPTY_ARRAY);
  }

  private static class EnableHighlighterAction extends BooleanPropertyToggleAction {
    @NotNull private final VcsLogHighlighterFactory myFactory;

    private EnableHighlighterAction(@NotNull VcsLogHighlighterFactory factory) {
      super(factory.getTitle());
      myFactory = factory;
    }

    @Override
    protected VcsLogUiProperties.VcsLogUiProperty<Boolean> getProperty() {
      return VcsLogHighlighterProperty.get(myFactory.getId());
    }
  }
}
