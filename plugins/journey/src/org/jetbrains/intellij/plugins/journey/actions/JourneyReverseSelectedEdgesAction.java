package org.jetbrains.intellij.plugins.journey.actions;

import com.intellij.diagram.DiagramAction;
import com.intellij.diagram.DiagramBuilder;
import com.intellij.diagram.util.DiagramSelectionService;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.LayeredIcon;
import com.intellij.uml.UmlIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.intellij.plugins.journey.diagram.JourneyDiagramDataModel;
import org.jetbrains.intellij.plugins.journey.diagram.JourneyEdge;

import javax.swing.*;
import java.util.List;

public class JourneyReverseSelectedEdgesAction extends DiagramAction {
  private static final Logger LOG = Logger.getInstance(JourneyReverseSelectedEdgesAction.class);
  public static final String NAME = "Reverse edges";

  private static final Icon ICON;
  static {
    LayeredIcon icon = new LayeredIcon(2);
    icon.setIcon(UmlIcons.Edgemode, 0);
    icon.setIcon(AllIcons.Diff.Revert, 1, 10, 10);
    ICON = icon;
  }

  public JourneyReverseSelectedEdgesAction() {
    super(NAME, NAME, ICON);
  }

  @Override
  public void perform(@NotNull AnActionEvent e) {
    final var builder = getBuilder(e);
    if (builder == null) return;
    if (!(getDataModel(e) instanceof JourneyDiagramDataModel dataModel)) return;

    List<JourneyEdge> list = DiagramSelectionService.getInstance().getSelectedEdges(builder)
      .stream()
      .filter(it -> it instanceof JourneyEdge)
      .map(it -> (JourneyEdge)it)
      .toList();

    if (list.isEmpty()) {
      LOG.warn("No edges selected");
      return;
    }

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      for (JourneyEdge edge : list) {
        dataModel.removeEdge(edge);
        dataModel.createEdge(edge.getTarget(), edge.getSource());
      }
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
