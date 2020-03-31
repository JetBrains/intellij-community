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
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public class AnnotateActionGroup extends ActionGroup implements DumbAware {
  private final AnAction[] myActions;

  public AnnotateActionGroup(@NotNull List<AnnotationFieldGutter> gutters,
                             @Nullable Couple<Map<VcsRevisionNumber, Color>> bgColorMap) {
    super(VcsBundle.message("annotate.action.view.group.text"), true);
    final List<AnAction> actions = new ArrayList<>();
    for (AnnotationFieldGutter g : gutters) {
      if (g.getID() != null) {
        actions.add(new ShowHideAspectAction(g));
      }
    }
    actions.add(Separator.getInstance());
    if (bgColorMap != null) {
      actions.add(new ShowAnnotationColorsAction());
    }
    actions.add(new ShowShortenNames());
    myActions = actions.toArray(AnAction.EMPTY_ARRAY);
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    return myActions;
  }

  static void revalidateMarkupInAllEditors() {
    for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
      if (editor.getGutter() instanceof EditorGutterComponentEx) {
        ((EditorGutterComponentEx)editor.getGutter()).revalidateMarkup();
      }
    }
  }
}
