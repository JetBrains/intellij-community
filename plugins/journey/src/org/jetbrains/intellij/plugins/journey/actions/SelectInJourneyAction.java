package org.jetbrains.intellij.plugins.journey.actions;

import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.intellij.plugins.journey.diagram.JourneyDiagramDataModel;
import org.jetbrains.intellij.plugins.journey.util.JourneyDiagramDataModelUtil;
import org.jetbrains.intellij.plugins.journey.diagram.JourneyDiagramProvider;
import org.jetbrains.intellij.plugins.journey.diagram.JourneyNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SelectInJourneyAction implements SelectInTarget {

  private static final Logger LOG = Logger.getInstance(SelectInJourneyAction.class);

  @Override
  public @Nls String toString() {
    return "Journey";
  }

  @Override
  public float getWeight() {
    return 100.0f; // To be the last in the list.
  }

  @Override
  public boolean canSelect(SelectInContext context) {
    List<JourneyDiagramDataModel> models = JourneyDiagramProvider.getInstance().getModels();
    if (models.isEmpty()) {
      return false;
    }
    for (JourneyDiagramDataModel model : models) {
      List<JourneyNode> nodes = JourneyDiagramDataModelUtil.findNodesForFile(model, context.getVirtualFile());
      if (!nodes.isEmpty()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void selectIn(SelectInContext context, boolean requestFocus) {
    Project project = context.getProject();
    List<JourneyDiagramDataModel> models = JourneyDiagramProvider.getInstance().getModels();
    Map<JourneyDiagramDataModel, JourneyNode> found = new HashMap<>();
    for (JourneyDiagramDataModel model : models) {
      List<JourneyNode> nodes = JourneyDiagramDataModelUtil.findNodesForFile(model, context.getVirtualFile());
      if (nodes.size() > 1) {
        LOG.error("Found more than one node for file " + context.getVirtualFile() + " in model " + model.getOriginalElement());
      }
      JourneyNode node = ContainerUtil.getFirstItem(nodes);
      if (node != null) {
        found.put(model, node);
      }
    }

    if (found.isEmpty()) {
      LOG.warn("No nodes found for file " + context.getVirtualFile());
    }

    if (found.size() > 1) {
      // TODO
    }

    Map.Entry<JourneyDiagramDataModel, JourneyNode> entry = found.entrySet().stream().findFirst().get();
    JourneyDiagramDataModel dataModel = entry.getKey();
    JourneyNode node = entry.getValue();
    JourneyDiagramDataModelUtil.openTabAndFocus(dataModel);
    node.navigate(requestFocus);
  }
}
