package org.jetbrains.intellij.plugins.journey.actions;

import com.intellij.openapi.actionSystem.ActionPromoter;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.intellij.plugins.journey.JourneyDataKeys;

import java.util.List;

/**
 * Promotes Journey actions to be executed in Journey editors.
 * Used to override default shortcuts in editor.
 */
public interface JourneyEditorOverrideActionPromoter extends ActionPromoter {

  @Override @Unmodifiable @Nullable
  default List<AnAction> promote(@NotNull List<? extends AnAction> actions, @NotNull DataContext context) {
    Editor editor = context.getData(CommonDataKeys.EDITOR);
    if (editor == null) return null;

    if (editor.getUserData(JourneyDataKeys.JOURNEY_DIAGRAM_DATA_MODEL) == null) {
      return null;
    }

    List<AnAction> promoted = actions.stream()
      .filter(it -> it instanceof JourneyEditorOverrideActionPromoter)
      .map(it -> (AnAction)it)
      .toList();

    return promoted;
  }

}
