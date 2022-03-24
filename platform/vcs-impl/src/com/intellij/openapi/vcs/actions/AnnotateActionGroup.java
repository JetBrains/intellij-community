// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.vcs.annotate.FileAnnotation;
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

  /**
   * @deprecated It is an internal API, try to avoid using it.
   */
  @Deprecated
  public AnnotateActionGroup(@NotNull List<AnnotationFieldGutter> gutters,
                             @Nullable Couple<Map<VcsRevisionNumber, Color>> bgColorMap) {
    this(null, gutters, bgColorMap);
  }

  public AnnotateActionGroup(@Nullable FileAnnotation fileAnnotation,
                             @NotNull List<AnnotationFieldGutter> gutters,
                             @Nullable Couple<Map<VcsRevisionNumber, Color>> bgColorMap) {
    super(VcsBundle.message("annotate.action.view.group.text"), true);
    final List<AnAction> actions = new ArrayList<>();
    for (AnnotationFieldGutter g : gutters) {
      if (g.getID() != null && g.getDisplayName() != null) {
        actions.add(new ShowHideAspectAction(g));
      }
    }
    actions.add(Separator.getInstance());
    if (bgColorMap != null) {
      actions.add(new ShowAnnotationColorsAction());
    }
    actions.add(new ShowShortenNames());
    if (fileAnnotation != null) {
      actions.add(Separator.getInstance());
      actions.add(new AnnotateDiffOnHoverToggleAction(fileAnnotation));
    }
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
