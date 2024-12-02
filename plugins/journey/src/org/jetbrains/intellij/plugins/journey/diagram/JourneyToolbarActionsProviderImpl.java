package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.DiagramBuilder;
import com.intellij.diagram.actions.DiagramToolbarActionsProvider;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.uml.UmlFileEditorImpl;
import com.intellij.uml.core.actions.UmlActions;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class JourneyToolbarActionsProviderImpl implements DiagramToolbarActionsProvider {

  @Override
  public void addToolbarActionsTo(@NotNull DefaultActionGroup group, @NotNull DiagramBuilder builder) {
    DefaultActionGroup defaultActionGroup = UmlActions.getCommonToolbarActions();
    var exceptionList = List.of("Copy Diagram to Clipboard", "Copy Selection to Clipboard", "Open Diagrams Settings", "Context Help");
    var exceptions = Arrays.stream(defaultActionGroup.getChildActionsOrStubs()).
      filter(it -> (it.getTemplateText() != null && exceptionList.contains(it.getTemplateText())));
    exceptions.forEach(defaultActionGroup::remove);
    group.add(defaultActionGroup);
  }

  @Override
  public @Nullable DefaultActionGroup getCurrentToolbarActions(@NotNull DiagramBuilder builder) {
    return Optional.ofNullable(builder.getEditor())
      .map(it -> ObjectUtils.tryCast(it, UmlFileEditorImpl.class))
      .map(it -> it.getGraphComponent().getToolbarActionGroup())
      .orElse(null);
  }

}
