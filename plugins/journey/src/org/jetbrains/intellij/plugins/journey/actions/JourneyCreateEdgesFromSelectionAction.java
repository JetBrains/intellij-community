package org.jetbrains.intellij.plugins.journey.actions;

import com.intellij.diagram.DiagramAction;
import com.intellij.diagram.DiagramBuilder;
import com.intellij.diagram.util.DiagramSelectionService;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uml.UmlIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.intellij.plugins.journey.diagram.JourneyDiagramDataModel;
import org.jetbrains.intellij.plugins.journey.diagram.JourneyNode;

import java.util.List;

public class JourneyCreateEdgesFromSelectionAction extends DiagramAction {
  private static final Logger LOG = Logger.getInstance(JourneyCreateEdgesFromSelectionAction.class);
  public static final String NAME = "Create Edge From Selection";

  public JourneyCreateEdgesFromSelectionAction() {
    super(NAME, NAME, UmlIcons.Edgemode);
  }

  @Override
  public void perform(@NotNull AnActionEvent e) {
    final var builder = getBuilder(e);
    if (builder == null) return;
    if (!(getDataModel(e) instanceof JourneyDiagramDataModel dataModel)) return;

    List<JourneyNode> list = DiagramSelectionService.getInstance().getSelectedNodes(builder)
      .stream()
      .filter(it -> it instanceof JourneyNode)
      .map(it -> (JourneyNode)it)
      .toList();

    if (list.size() != 2) {
      LOG.warn("There should be only 2 selected elements, but was " + list.size());
      return;
    }

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      return dataModel.createEdge(list.get(0), list.get(1));
    });
  }

  @Override
  public boolean isEnabled(@NotNull AnActionEvent e, @NotNull DiagramBuilder b) {
    return b.getDataModel() instanceof JourneyDiagramDataModel;
  }

  @Override
  public @NotNull @Nls String getActionName() {
    return NAME;
  }

}
