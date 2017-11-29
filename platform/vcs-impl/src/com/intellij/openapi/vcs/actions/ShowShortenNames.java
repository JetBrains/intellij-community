/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * @author Konstantin Bulenkov
 */
public class ShowShortenNames extends ActionGroup {
  private final AnAction[] myChildren;

  public ShowShortenNames() {
    super("Names", true);
    final ArrayList<AnAction> kids = new ArrayList<>(ShortNameType.values().length);
    for (ShortNameType type : ShortNameType.values()) {
      kids.add(new SetShortNameTypeAction(type));
    }
    myChildren = kids.toArray(new AnAction[kids.size()]);
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    return myChildren;
  }

  public static ShortNameType getType() {
    for (ShortNameType type : ShortNameType.values()) {
      if (type.isSet()) {
        return type;
      }
    }
    return ShortNameType.LASTNAME;
  }

  private static class SetShortNameTypeAction extends ToggleAction implements DumbAware {
    private final ShortNameType myType;

    public SetShortNameTypeAction(ShortNameType type) {
      super(type.getDescription());
      myType = type;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myType == getType();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean enabled) {
      if (enabled) {
        myType.set();
      }

      AnnotateActionGroup.revalidateMarkupInAllEditors();
    }
  }
}
